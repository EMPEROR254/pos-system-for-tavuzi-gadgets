// Product search and IMEIs for the till: from the server, or - when it can't
// be reached - from the copy of the catalogue saved on this till.
import { lookupProducts, type SellableProduct } from '../../services/products'
import { isUnreachable, searchOffline, serialsOffline } from '../../services/offline'
import { serialsInStock } from '../../services/stock'

export async function findProducts(q: string): Promise<SellableProduct[]> {
  try {
    return await lookupProducts(q)
  } catch (e) {
    if (isUnreachable(e)) return searchOffline(q)
    throw e
  }
}

export async function findSerials(productId: number): Promise<string[]> {
  try {
    return await serialsInStock(productId)
  } catch (e) {
    if (isUnreachable(e)) return serialsOffline(productId)
    throw e
  }
}
