package com.learningcompanion.library.application.ingestion;

import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tác vụ nền xử lý tài liệu (NCL-05-CN-003). Tài liệu vào hàng chờ được nhận ngay sau khi lưu xong; ngoài ra hàng chờ
 * được quét định kỳ để nhận tài liệu còn sót (máy chủ khởi động lại, tài liệu đang xử lý dở quá lâu). Mỗi máy chủ xử
 * lý tối đa {@code workers} tài liệu cùng lúc; việc nhận tài liệu đi qua cơ sở dữ liệu nên nhiều máy chủ chạy cùng lúc
 * vẫn không xử lý trùng.
 */
@Component
public class IngestionJob {

    private static final Logger log = LoggerFactory.getLogger(IngestionJob.class);
    private static final int SWEEP_LIMIT = 50;

    private final IngestionPipeline pipeline;
    private final JdbcTemplate jdbc;
    private final ProcessingProperties properties;
    private final Clock clock;
    private final ExecutorService workers;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    public IngestionJob(IngestionPipeline pipeline, JdbcTemplate jdbc, ProcessingProperties properties, Clock clock) {
        this.pipeline = pipeline;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        this.running = properties.enabled();
        this.workers = Executors.newFixedThreadPool(properties.workers(),
                Thread.ofVirtual().name("document-processing-", 0).factory());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(DocumentQueued event) {
        submit(event);
    }

    /** Quét hàng chờ: tài liệu chờ xử lý chưa được nhận và tài liệu đang xử lý dở quá lâu. */
    @Scheduled(fixedDelayString = "${app.library.processing.sweep-interval:PT30S}", initialDelay = 5_000)
    public void sweep() {
        if (!running) {
            return;
        }
        Instant staleBefore = clock.instant().minus(properties.staleAfter());
        jdbc.query("select id, owner_id from documents where trashed_at is null and (processing_status = 'PENDING' "
                        + "or (processing_status = 'PROCESSING' and processing_started_at < ?)) "
                        + "order by created_at limit " + SWEEP_LIMIT,
                (row) -> {
                    submit(new DocumentQueued(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class)));
                }, Timestamp.from(staleBefore));
    }

    /** Dừng hoặc chạy lại tác vụ nền (công cụ phát triển); chạy lại thì quét hàng chờ ngay. */
    public void setRunning(boolean value) {
        running = value;
        if (value) {
            sweep();
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** Số tài liệu đang được máy chủ này xử lý. */
    public int inFlight() {
        return inFlight.size();
    }

    private void submit(DocumentQueued job) {
        if (!running || !inFlight.add(job.documentId())) {
            return;
        }
        try {
            workers.submit(() -> {
                try {
                    pipeline.process(job);
                } catch (RuntimeException ex) {
                    log.warn("Không xử lý được tài liệu {}", job.documentId(), ex);
                } finally {
                    inFlight.remove(job.documentId());
                }
            });
        } catch (RuntimeException ex) {
            inFlight.remove(job.documentId());
            throw ex;
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }
}
