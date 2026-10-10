const providers: { name: string; url: string; domains: string[] }[] = [
  { name: 'Gmail', url: 'https://mail.google.com/', domains: ['gmail.com', 'googlemail.com'] },
  {
    name: 'Outlook',
    url: 'https://outlook.live.com/mail/',
    domains: ['outlook.com', 'hotmail.com', 'live.com', 'msn.com', 'outlook.com.vn'],
  },
  { name: 'Yahoo Mail', url: 'https://mail.yahoo.com/', domains: ['yahoo.com', 'yahoo.com.vn', 'ymail.com'] },
  { name: 'iCloud Mail', url: 'https://www.icloud.com/mail', domains: ['icloud.com', 'me.com', 'mac.com'] },
]

/** Nút "Mở Gmail" chỉ hiện với các nhà cung cấp phổ biến đoán được từ tên miền. */
export function detectMailProvider(email: string) {
  const domain = email.split('@')[1]?.toLowerCase()
  return providers.find((provider) => domain && provider.domains.includes(domain)) ?? null
}
