import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en/common.json'
import vi from './locales/vi/common.json'

export const defaultLanguage = 'vi'

void i18n.use(initReactI18next).init({
  resources: { vi: { common: vi }, en: { common: en } },
  lng: defaultLanguage,
  fallbackLng: defaultLanguage,
  defaultNS: 'common',
  interpolation: { escapeValue: false },
})

export default i18n
