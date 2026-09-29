// Sample products shared by tests.
import type { SellableProduct } from '../services/products'

export const phone: SellableProduct = {
  id: 1,
  name: 'Tecno Spark 20',
  sku: 'TS20',
  vatCategory: 'STANDARD',
  vatRate: 16,
  serialType: 'IMEI',
  warrantyMonths: 12,
  stockOnHand: 3,
  units: [{ id: 11, name: 'Piece', factor: 1, barcode: null, retailPrice: 11600, wholesalePrice: 11000 }],
}

export const cable: SellableProduct = {
  id: 2,
  name: 'USB cable',
  sku: null,
  vatCategory: 'EXEMPT',
  vatRate: 0,
  serialType: 'NONE',
  warrantyMonths: 0,
  stockOnHand: 40,
  units: [
    { id: 21, name: 'Piece', factor: 1, barcode: 'CBL1', retailPrice: 200, wholesalePrice: null },
    { id: 22, name: 'Carton of 20', factor: 20, barcode: null, retailPrice: 3600, wholesalePrice: 3000 },
  ],
}
