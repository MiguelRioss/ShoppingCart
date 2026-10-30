import { fileURLToPath } from 'node:url';
try { process.loadEnvFile(fileURLToPath(new URL('../.env', import.meta.url))); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const args = process.argv.slice(2);
const option = (name, fallback) => { const index = args.indexOf(`--${name}`); return index < 0 ? fallback : args[index + 1]; };
const route = option('route', 'all');
const base = option('base-url', process.env.EMAIL_SENDER_BASE_URL || 'https://email-sender-0fks.onrender.com').replace(/\/$/, '');
const api = base.endsWith('/v2') ? base : base + '/v2';
const key = process.env.EMAIL_SENDER_API_KEY;
let reference = option('reference', `API-TEST-${randomUUID()}`);
const recipient = 'miguelangelorios5f@gmail.com';
const allowed = ['all', 'templates', 'render', 'auth', 'send', 'history', 'schedule'];
if (!allowed.includes(route)) { console.error('Choose --route templates, render, auth, send, history, schedule or all'); process.exit(1); }

async function request(path, method = 'GET', body, authenticated = false, expected = 200) {
  if (authenticated && !key) throw Error('Set EMAIL_SENDER_API_KEY in the root .env first');
  let response;
  try {
    response = await fetch(api + path, { method, signal: AbortSignal.timeout(90000),
      headers: { 'Content-Type': 'application/json', ...(authenticated ? { Authorization: `Bearer ${key}` } : {}) },
      ...(body == null ? {} : { body: JSON.stringify(body) }) });
  } catch { throw Error(`${method} ${path}: could not reach the server`); }
  const text = await response.text();
  let result; try { result = JSON.parse(text); } catch { result = {}; }
  if (response.status !== expected) throw Error(`${method} ${path}: HTTP ${response.status}${typeof result.message === 'string' ? ' — ' + result.message : ''}`);
  return result;
}

function variables(template) {
  const values = {
    customer_first_name: 'Miguel', supplier_contact_name: 'Miguel (supplier test)', order_reference: reference,
    invoice_number: 'API-TEST-NOT-AN-INVOICE', po_number: 'API-TEST-PO', packing_list_number: 'API-TEST-PL', delivery_note_number: 'API-TEST-DN',
    expected_despatch_date: 'Test estimate only', required_despatch_date: 'Test estimate only', lead_time_summary: 'API test product: 2–4 Weeks (test only)',
    previous_despatch_date: '1 December 2026', revised_despatch_date: '8 December 2026', estimated_delivery_date: '10 December 2026', delivery_date: '10 December 2026',
    boxes: '2', quantity_units: '2', quantity_m2: '1', package_count: '2', gross_weight: '10 kg', package_dimensions: '40 x 40 x 20 cm',
    courier: 'API TEST — no shipment booked', tracking_number: 'API-TEST-NO-TRACKING', incoterm: 'TEST ONLY', tracking_url: 'https://example.com/test-tracking',
    discount_code: 'API-TEST-NOT-REDEEMABLE', discount_terms: 'Test only', marketing_consent_link: 'https://example.com/test-consent'
  };
  const result = Object.fromEntries(template.requiredVariables.map(name => [name, values[name] ?? 'API test value']));
  if (template.productCards) result.products = [{ productId: 1, name: 'API test product — not a real order', quantity: '1', unit: 'm²', lineTotal: '90.00', currency: 'EUR' }];
  return result;
}


try {
  console.log(`EMAIL_SENDER V2: ${api}`);
  if (['all', 'templates', 'render', 'send'].includes(route)) {
    const { templates } = await request('/templates');
    if (!Array.isArray(templates) || !templates.length) throw Error('Templates response is invalid');
    if (route === 'all' || route === 'templates') {
      for (const template of templates) await request(`/templates/${encodeURIComponent(template.id)}`);
      console.log(`PASS: list templates and template details (${templates.length})`);
    }
    if (route === 'all' || route === 'render') {
      for (const template of templates) {
        const rendered = await request('/render', 'POST', { templateId: template.id, variables: variables(template) });
        if (!rendered.subject || !rendered.html?.includes('cid:deferranti-logo') || rendered.html.includes('{{')) throw Error(`Invalid rendered result for ${template.id}`);
      }
      console.log(`PASS: render all ${templates.length} templates (no emails sent)`);
    }
    if (route === 'send') {
      const requestFile = option('request-file');
      if (!requestFile) throw Error('Sending requires --request-file with real data and the approved PDF attachment');
      const payload = JSON.parse(await readFile(requestFile, 'utf8'));
      if (!payload.variables?.order_reference || !payload.idempotencyKey || !payload.to?.email) throw Error('The request file must contain order reference, recipient and idempotency key');
      reference = payload.variables.order_reference;
      const template = templates.find(item => item.id === payload.templateId);
      if (!template) throw Error('Unknown template ID in request file');
      const sent = await request('/send', 'POST', payload, true, 202);
      if (sent.status !== 'accepted' || sent.to !== recipient || sent.testMode !== true) throw Error('Unexpected send result or recipient override — inspect the server');
      console.log(`PASS: SMTP accepted ${template.id} for ${recipient}`);
      const repeated = await request('/send', 'POST', payload, true, 202);
      if (sent.messageId !== repeated.messageId) throw Error('Duplicate submission returned another SMTP message ID');
      console.log('PASS: repeating the API call returned the same saved email');
      const history = await request(`/emails?orderReference=${encodeURIComponent(reference)}`, 'GET', null, true);
      const matching = history.emails?.filter(email => email.smtp_message_id === sent.messageId && email.template_id === template.id);
      if (matching?.length !== 1 || matching[0].status !== 'accepted') throw Error('Expected one accepted record for this email in history');
      console.log('PASS: Neon history contains one accepted email');
      console.log(`Reference: ${reference}`);
    }
  }
  if (route === 'all' || route === 'auth') {
    await request('/send', 'POST', {}, false, 401);
    await request('/send', 'POST', {}, true, 400);
    await request(`/emails?orderReference=${encodeURIComponent(reference)}`, 'GET', null, false, 401);
    console.log('PASS: send/history authentication and send input validation (no emails sent)');
  }
  if (route === 'all' || route === 'schedule') {
    await request('/schedule', 'POST', {}, false, 501);
    console.log('PASS: scheduler correctly reports not implemented (501)');
  }
  if (route === 'all' || route === 'history') {
    const history = await request(`/emails?orderReference=${encodeURIComponent(reference)}`, 'GET', null, true);
    if (!Array.isArray(history.emails)) throw Error('Invalid history response');
    console.log(`PASS: authenticated email history (${history.emails.length} records)`);
  }
  if (route !== 'send') console.log('Finished. No real emails sent.');
} catch (error) { console.error(`FAIL: ${error.message}`); process.exitCode = 1; }
