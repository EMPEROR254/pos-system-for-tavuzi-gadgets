import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { canScanWithCamera } from '../../components/CameraScanner'
import * as hardware from '../../services/hardware'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/hardware', () => ({
  getPrinter: vi.fn(),
  savePrinter: vi.fn(),
  testPrint: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(hardware.getPrinter).mockResolvedValue({
    host: null,
    port: 9100,
    width: 48,
    openDrawer: false,
    autoPrint: false,
  })
})

describe('HardwarePage', () => {
  it('saves the network printer and cash drawer settings', async () => {
    vi.mocked(hardware.savePrinter).mockImplementation(async (c) => c)
    renderApp('/settings/hardware', 'OWNER')
    await userEvent.type(await screen.findByLabelText('Printer IP address'), '192.168.1.50')
    await userEvent.selectOptions(screen.getByLabelText('Paper'), '32')
    await userEvent.click(screen.getByLabelText(/Open the cash drawer/))
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Printer settings saved.')).toBeInTheDocument()
    expect(hardware.savePrinter).toHaveBeenCalledWith({
      host: '192.168.1.50',
      port: 9100,
      width: 32,
      openDrawer: true,
      autoPrint: false,
    })
    // Recommends a printer, as the client asked.
    expect(screen.getByText(/80 mm thermal printer with an Ethernet/)).toBeInTheDocument()
  })

  it('test print is only possible once a printer is saved', async () => {
    renderApp('/settings/hardware', 'OWNER')
    expect(await screen.findByRole('button', { name: 'Print test page' })).toBeDisabled()
  })

  it('is for the owner only', async () => {
    renderApp('/settings/hardware', 'MANAGER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})

describe('camera scanning', () => {
  it('is offered only where the browser can detect barcodes', () => {
    expect(canScanWithCamera()).toBe(false)
    Object.assign(window, { BarcodeDetector: class {} })
    Object.assign(navigator, { mediaDevices: { getUserMedia: vi.fn() } })
    expect(canScanWithCamera()).toBe(true)
    delete (window as unknown as { BarcodeDetector?: unknown }).BarcodeDetector
  })
})
