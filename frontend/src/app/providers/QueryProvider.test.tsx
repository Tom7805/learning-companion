import { useQueryClient } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import { http as mswHttp, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { http } from '@/shared/api/httpClient'
import { queryKeys } from '@/shared/api/queryKeys'
import { server } from '@/test/mocks/server'
import { QueryProvider } from './QueryProvider'

function Probe({ onClient }: { onClient: (client: ReturnType<typeof useQueryClient>) => void }) {
  onClient(useQueryClient())
  return null
}

const lost = (code: string) => HttpResponse.json({ code, message: code, status: 401, fieldErrors: [] }, { status: 401 })

describe('QueryProvider · mất phiên', () => {
  it('yêu cầu song song: "chưa đăng nhập" về trước, lý do "bị thu hồi" về sau vẫn được giữ cho trang đăng nhập', async () => {
    server.use(
      mswHttp.get('*/api/v1/first', () => lost('UNAUTHENTICATED')),
      mswHttp.get('*/api/v1/second', () => lost('SESSION_REVOKED')),
    )
    let client!: ReturnType<typeof useQueryClient>
    render(
      <QueryProvider>
        <Probe onClient={(value) => (client = value)} />
      </QueryProvider>,
    )
    client.setQueryData(queryKeys.session, { authenticated: true })

    await http.get('/first').catch(() => undefined)
    expect(client.getQueryData(queryKeys.session)).toEqual({ authenticated: false, reason: undefined })
    await http.get('/second').catch(() => undefined)
    await waitFor(() =>
      expect(client.getQueryData(queryKeys.session)).toEqual({ authenticated: false, reason: 'SESSION_REVOKED' }),
    )

    // Đã có lý do cụ thể thì không bị lý do khác ghi đè.
    server.use(mswHttp.get('*/api/v1/third', () => lost('SESSION_EXPIRED')))
    await http.get('/third').catch(() => undefined)
    expect(client.getQueryData(queryKeys.session)).toEqual({ authenticated: false, reason: 'SESSION_REVOKED' })
  })
})
