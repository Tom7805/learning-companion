import { Send } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { SafetyNotice, type SafetyOutcome } from '@/features/safety'
import { Alert, Button } from '@/shared/ui'
import { streamAiTestCall, type AiTestCallDone } from '../lib/aiTestCall'
import { aiErrorMessage } from './aiErrors'

/** Gọi thử sinh văn bản qua lớp kết nối: chữ hiện dần theo luồng, cuối cùng ghi nhà cung cấp đã trả lời. */
export function AiTestCallPanel() {
  const { t } = useTranslation()
  const id = useId()
  const client = useQueryClient()
  const [prompt, setPrompt] = useState(() => t('aiGateway.test.defaultPrompt'))
  const [output, setOutput] = useState('')
  const [done, setDone] = useState<AiTestCallDone | null>(null)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const [safety, setSafety] = useState<SafetyOutcome | null>(null)
  const [running, setRunning] = useState(false)
  const abort = useRef<AbortController | null>(null)

  useEffect(() => () => abort.current?.abort(), [])

  const run = async (event: FormEvent) => {
    event.preventDefault()
    if (!prompt.trim() || running) return
    abort.current?.abort()
    abort.current = new AbortController()
    setOutput('')
    setDone(null)
    setErrorCode(null)
    setSafety(null)
    setRunning(true)
    try {
      await streamAiTestCall(
        prompt.trim(),
        {
          onChunk: (text) => setOutput((current) => current + text),
          onDone: setDone,
          onError: setErrorCode,
          onSafety: (outcome) => {
            // Nội dung sinh ra bị thay: bỏ phần đã hiện, chỉ còn thông báo chặn (NCL-04-CN-004 TC-04).
            setOutput('')
            setSafety(outcome)
          },
        },
        abort.current.signal,
      )
    } catch (failure) {
      if (!(failure instanceof DOMException && failure.name === 'AbortError')) setErrorCode('NETWORK_ERROR')
    } finally {
      setRunning(false)
      void client.invalidateQueries({ queryKey: ['admin', 'ai'] })
    }
  }

  return (
    <section
      aria-labelledby="ai-test-title"
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
    >
      <div className="flex flex-col gap-1">
        <h2 id="ai-test-title" className="text-xl font-semibold">
          {t('aiGateway.test.title')}
        </h2>
        <p className="text-sm text-muted">{t('aiGateway.test.subtitle')}</p>
      </div>
      <form onSubmit={run} className="flex flex-col gap-3">
        <label htmlFor={`${id}-prompt`} className="text-sm font-medium text-ink-soft">
          {t('aiGateway.test.prompt')}
        </label>
        <textarea
          id={`${id}-prompt`}
          value={prompt}
          onChange={(event) => setPrompt(event.target.value)}
          maxLength={2000}
          rows={2}
          className="w-full rounded-field border-[1.5px] border-ink bg-surface px-3 py-2.5 text-[0.9375rem] outline-none focus:border-focus focus:ring-4 focus:ring-focus/15"
        />
        <div>
          <Button
            type="submit"
            variant="accent"
            loading={running}
            icon={<Send className="size-4" aria-hidden="true" />}
          >
            {t('aiGateway.test.run')}
          </Button>
        </div>
      </form>
      {safety ? (
        <SafetyNotice outcome={safety} />
      ) : (
        <div
          aria-live="polite"
          aria-busy={running || undefined}
          className="min-h-16 whitespace-pre-wrap rounded-field border border-line bg-paper px-4 py-3 text-[0.9375rem] leading-relaxed"
          data-testid="ai-test-output"
        >
          {output || (
            <span className="text-muted">{t(running ? 'aiGateway.test.waiting' : 'aiGateway.test.empty')}</span>
          )}
        </div>
      )}
      {done?.providerCode && (
        <p className="text-sm text-ink-soft" data-testid="ai-test-done">
          {t('aiGateway.test.done', {
            provider: done.providerCode,
            input: done.inputUnits,
            output: done.outputUnits,
          })}
        </p>
      )}
      {errorCode && (
        <Alert tone="danger" data-testid="ai-test-error">
          <p>{aiErrorMessage(t, { code: errorCode, message: '', status: 0, fieldErrors: [] })}</p>
        </Alert>
      )}
    </section>
  )
}
