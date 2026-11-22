// PROPOSAL. Do not deploy this function from the Android pull request.
// Secrets stay in Supabase only:
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON  (Play Developer API service account)
//   PLAY_PACKAGE_NAME                 (com.ahmed.rotrun)
// Never put the service-account JSON in the app or this repository.
//
// The Android client sends SHA-256 hex of the Supabase user id as
// obfuscatedAccountId. This function accepts that hash or the raw user id.

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUBSCRIPTIONS = new Set([
  'com.ahmed.rotrun.subscription.monthly',
  'com.ahmed.rotrun.subscription.yearly',
  'com.ahmed.rotrun.subscription.yearly70',
])
const LIFETIME = 'com.ahmed.rotrun.lifetime'
const PUBLISHER = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications'
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher'

const corsHeaders: Record<string, string> = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-rotrun-platform, x-rotrun-build',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

type PurchaseIn = { product_id?: unknown; purchase_token?: unknown; kind?: unknown }
type EntitlementOut = { product_id: string; expires_at: string | null; active: boolean }

let cachedToken: { value: string; exp: number } | null = null

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  })
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----/g, '').replace(/-----END PRIVATE KEY-----/g, '').replace(/\s+/g, '')
  const binary = atob(body)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

function b64url(bytes: Uint8Array): string {
  let raw = ''
  for (const byte of bytes) raw += String.fromCharCode(byte)
  return btoa(raw).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

async function sha256hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

async function googleAccessToken(account: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  if (cachedToken && cachedToken.exp > now + 60) return cachedToken.value
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })))
  const claim = b64url(new TextEncoder().encode(JSON.stringify({
    iss: account.client_email,
    scope: SCOPE,
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  })))
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToDer(account.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signed = new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(`${header}.${claim}`)))
  const assertion = `${header}.${claim}.${b64url(signed)}`
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  })
  if (!response.ok) throw new Error('play auth failed')
  const payload = await response.json()
  cachedToken = { value: payload.access_token, exp: now + Number(payload.expires_in ?? 3600) }
  return cachedToken.value
}

function accountMatches(obfuscated: string | undefined, userId: string, hash: string): boolean {
  if (!obfuscated) return false
  return obfuscated === hash || obfuscated === userId
}

function leastIso(expiry: string | undefined, now: Date): string {
  const parsed = expiry ? new Date(expiry).getTime() : Number.NaN
  const cutoff = Number.isFinite(parsed) ? Math.min(parsed, now.getTime()) : now.getTime()
  return new Date(cutoff).toISOString()
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })
  if (req.method !== 'POST') return json({ error: 'Method not allowed' }, 405)

  const authHeader = req.headers.get('Authorization')
  if (!authHeader?.startsWith('Bearer ')) return json({ error: 'Missing authorization' }, 401)

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')
  const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  const packageName = Deno.env.get('PLAY_PACKAGE_NAME') || 'com.ahmed.rotrun'
  const serviceAccountRaw = Deno.env.get('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON')
  if (!supabaseUrl || !supabaseAnonKey || !supabaseServiceKey || !serviceAccountRaw) {
    return json({ error: 'Entitlement sync is unavailable' }, 503)
  }

  let serviceAccount: { client_email: string; private_key: string }
  try {
    serviceAccount = JSON.parse(serviceAccountRaw)
  } catch {
    return json({ error: 'Entitlement sync is unavailable' }, 503)
  }

  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: authHeader } },
  })
  const { data: { user }, error: authError } = await supabase.auth.getUser()
  if (authError || !user) return json({ error: 'Invalid token' }, 401)
  const userHash = await sha256hex(user.id)

  let body: { purchases?: unknown }
  try {
    body = await req.json()
  } catch {
    return json({ error: 'Expected a JSON body' }, 400)
  }
  const purchases = body.purchases
  if (!Array.isArray(purchases) || purchases.length === 0) return json({ error: 'purchases must be a non-empty list' }, 400)
  if (purchases.length > 8) return json({ error: 'Too many purchases' }, 400)

  let accessToken: string
  try {
    accessToken = await googleAccessToken(serviceAccount)
  } catch {
    return json({ error: 'Entitlement sync is unavailable' }, 503)
  }

  const admin = createClient(supabaseUrl, supabaseServiceKey)
  let updated = 0
  let rejected = 0
  const entitlements: EntitlementOut[] = []

  for (const item of purchases as PurchaseIn[]) {
    const productId = String(item.product_id ?? '')
    const token = String(item.purchase_token ?? '')
    const kind = String(item.kind ?? '')
    if (!productId || !token || (kind !== 'subs' && kind !== 'inapp')) {
      rejected += 1
      continue
    }
    try {
      const row = kind === 'inapp'
        ? await verifyLifetime(packageName, productId, token, accessToken, user.id, userHash)
        : await verifySubscription(packageName, productId, token, accessToken, user.id, userHash)
      if (!row) {
        rejected += 1
        continue
      }
      const original = `gp:${await sha256hex(token)}`
      const { error } = await admin.from('entitlements').upsert({
        user_id: user.id,
        product_id: row.product_id,
        original_transaction_id: original,
        expires_at: row.expires_at,
        environment: row.environment,
        platform: 'android',
        updated_at: new Date().toISOString(),
      }, { onConflict: 'user_id,original_transaction_id' })
      if (error) throw new Error('upsert failed')
      entitlements.push({ product_id: row.product_id, expires_at: row.expires_at, active: row.active })
      updated += 1
    } catch {
      rejected += 1
    }
  }

  if (updated === 0) return json({ error: 'No purchase could be verified', updated, rejected }, 400)
  return json({ updated, rejected, entitlements })
})

async function playGet(url: string, accessToken: string): Promise<Response> {
  return await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } })
}

async function acknowledge(url: string, accessToken: string) {
  await fetch(url, { method: 'POST', headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' }, body: '{}' })
}

async function verifySubscription(
  packageName: string,
  productId: string,
  token: string,
  accessToken: string,
  userId: string,
  userHash: string,
): Promise<{ product_id: string; expires_at: string | null; active: boolean; environment: string } | null> {
  if (!SUBSCRIPTIONS.has(productId)) return null
  const response = await playGet(`${PUBLISHER}/${packageName}/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`, accessToken)
  if (!response.ok) return null
  const payload = await response.json()
  const obfuscated = payload?.externalAccountIdentifiers?.obfuscatedExternalAccountId as string | undefined
  if (!accountMatches(obfuscated, userId, userHash)) return null
  const items = Array.isArray(payload.lineItems) ? payload.lineItems : []
  const item = items.find((row: { productId?: string }) => row.productId === productId) ?? items[0]
  const foundProduct = String(item?.productId ?? '')
  if (!SUBSCRIPTIONS.has(foundProduct)) return null
  const expiry = typeof item?.expiryTime === 'string' ? item.expiryTime : undefined
  const state = String(payload.subscriptionState ?? '')
  const now = new Date()
  let active = false
  let expiresAt: string | null = expiry ?? null
  if (state === 'SUBSCRIPTION_STATE_ACTIVE' || state === 'SUBSCRIPTION_STATE_CANCELED') {
    active = true
    expiresAt = expiry ?? null
  } else if (state === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD') {
    const expiryMs = expiry ? new Date(expiry).getTime() : Number.NaN
    if (!Number.isFinite(expiryMs) || expiryMs <= now.getTime()) {
      expiresAt = new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString()
    }
    active = true
  } else if (
    state === 'SUBSCRIPTION_STATE_ON_HOLD' ||
    state === 'SUBSCRIPTION_STATE_PAUSED' ||
    state === 'SUBSCRIPTION_STATE_EXPIRED' ||
    state === 'SUBSCRIPTION_STATE_PENDING' ||
    state === 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED'
  ) {
    active = false
    expiresAt = leastIso(expiry, now)
  } else {
    return null
  }
  if (payload.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
    await acknowledge(
      `${PUBLISHER}/${packageName}/purchases/subscriptions/${encodeURIComponent(foundProduct)}/tokens/${encodeURIComponent(token)}:acknowledge`,
      accessToken,
    )
  }
  return {
    product_id: foundProduct,
    expires_at: expiresAt,
    active,
    environment: payload.testPurchase ? 'Sandbox' : 'Production',
  }
}

async function verifyLifetime(
  packageName: string,
  productId: string,
  token: string,
  accessToken: string,
  userId: string,
  userHash: string,
): Promise<{ product_id: string; expires_at: string | null; active: boolean; environment: string } | null> {
  if (productId !== LIFETIME) return null
  const response = await playGet(
    `${PUBLISHER}/${packageName}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`,
    accessToken,
  )
  if (!response.ok) return null
  const payload = await response.json()
  if (!accountMatches(payload?.obfuscatedExternalAccountId, userId, userHash)) return null
  const state = Number(payload.purchaseState)
  if (state === 2) return null
  const now = new Date().toISOString()
  const active = state === 0
  const expiresAt = state === 1 ? now : null
  if (state !== 0 && state !== 1) return null
  if (Number(payload.acknowledgementState) === 0) {
    await acknowledge(
      `${PUBLISHER}/${packageName}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}:acknowledge`,
      accessToken,
    )
  }
  return {
    product_id: productId,
    expires_at: expiresAt,
    active,
    environment: Number(payload.purchaseType) === 0 ? 'Sandbox' : 'Production',
  }
}
