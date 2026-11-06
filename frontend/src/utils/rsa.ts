/**
 * 密码传输加密：RSA-OAEP(SHA-256) 公钥加密（Web Crypto API，零依赖）。
 * 与后端 com.zhixue.util.RsaCipher 配对；后端解密失败会按明文兼容处理。
 */
const PUB_SPKI_B64 =
  'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo381JLl1zAlSOtePL42ONdNteaEzSWrKROwyo6Gdj06bkZNYWltSjNKUFWuT+i3Ugp/NtwanBpneKL/iWqeIhMpUJd/0Brg/Q/dErf5yOmXu/t4pSgGC7ujp/hl6K1Wb4AoSeVIMbA1Opv41QOHppAdk/1CxQ0U2aydUGBbGa9ZqaAZrLm55Yxj6hNJAcWTsBoKOphSFXpsGtr98Fu03wIZtnqLlah2JglHxYPflYdNSHnDDNWkiWLek1P1M/6bfMCL5s/gZKu5Ls0wHPUalL+kTQ/7DftmNok2ur5QqkhkQuRGrnPsjCbZCOFOkCzSgQi1JNHGDnYPWRZcBJMK03QIDAQAB'

let keyPromise: Promise<CryptoKey> | null = null

function getKey(): Promise<CryptoKey> {
  if (!keyPromise) {
    const bin = atob(PUB_SPKI_B64)
    const der = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) der[i] = bin.charCodeAt(i)
    keyPromise = crypto.subtle.importKey('spki', der.buffer, { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt'])
  }
  return keyPromise
}

/** 明文密码 → Base64 密文；环境不支持（非安全上下文等）时原样返回，由后端明文兼容 */
export async function encryptPassword(plain: string): Promise<string> {
  try {
    if (!plain || !window.crypto?.subtle) return plain
    const buf = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, await getKey(), new TextEncoder().encode(plain))
    let s = ''
    new Uint8Array(buf).forEach((b) => (s += String.fromCharCode(b)))
    return btoa(s)
  } catch {
    return plain
  }
}
