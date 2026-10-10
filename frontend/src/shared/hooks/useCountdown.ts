import { useEffect, useState } from 'react'

/** Mốc thời gian (ms) sau `seconds` giây kể từ bây giờ. */
export function deadlineIn(seconds: number) {
  return Date.now() + seconds * 1000
}

/** Số giây còn lại tới một mốc thời gian (ms), cập nhật mỗi giây; mốc đã qua hoặc null trả 0. */
export function useCountdown(targetMs: number | null): number {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!targetMs) return
    const tick = () => {
      const current = Date.now()
      setNow(current)
      if (current >= targetMs) window.clearInterval(interval)
    }
    // Cập nhật ngay khi có mốc mới, tránh hiển thị giá trị cũ trong giây đầu.
    const first = window.setTimeout(tick, 0)
    const interval = window.setInterval(tick, 1000)
    return () => {
      window.clearTimeout(first)
      window.clearInterval(interval)
    }
  }, [targetMs])

  return targetMs ? Math.max(0, Math.ceil((targetMs - now) / 1000)) : 0
}

export function formatMinutesSeconds(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
