// Receipt printer settings (/api/hardware/printer).
import { request } from './api'

export type PrinterConfig = {
  host: string | null
  port: number
  width: number
  openDrawer: boolean
  autoPrint: boolean
}

export const getPrinter = () => request<PrinterConfig>('/api/hardware/printer')
export const savePrinter = (body: PrinterConfig) =>
  request<PrinterConfig>('/api/hardware/printer', { method: 'PUT', body })
export const testPrint = () => request<void>('/api/hardware/printer/test', { method: 'POST' })
