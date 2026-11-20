import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { api } from './api.js';

const password = 'Synthetic-password-for-test';
const admin = { id: 'admin-id', email: 'admin@example.test', role: 'ADMIN', active: true };
const student = { id: 'student-id', email: 'student@example.test', role: 'STUDENT', active: true };
let identity, users, calls, onRequest;
const response = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  window.history.replaceState({}, '', '/');
  window.localStorage.clear(); window.sessionStorage.clear();
  identity = admin; users = [admin, student]; calls = []; onRequest = null;
  vi.stubGlobal('fetch', vi.fn(async (path, options) => {
    const call = { path, ...options, body: options.body ? JSON.parse(options.body) : null };
    calls.push(call);
    if (onRequest) { const overridden = await onRequest(call); if (overridden) return overridden; }
    if (path.endsWith('/auth/login')) return response({ accessToken: 'synthetic-memory-token', tokenType: 'Bearer', expiresIn: 900 });
    if (path.endsWith('/auth/me')) return response(identity);
    if (options.method === 'POST' && path.endsWith('/users')) {
      const user = { ...call.body, id: 'created-id' }; delete user.password; users = [...users, user]; return response(user, 201);
    }
    if (options.method === 'PATCH') {
      users = users.map(user => path.includes(user.id) ? { ...user, active: call.body.active } : user);
      return response(users.find(user => path.includes(user.id)));
    }
    if (path.includes('/users?')) return response({ items: users, total: users.length, page: 0, size: 10 });
    throw new Error('Unexpected test request');
  }));
});
afterEach(() => vi.unstubAllGlobals());

async function signIn() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email address'), identity.email);
  await user.type(screen.getByLabelText('Password', { exact: true }), password);
  await user.click(screen.getByRole('button', { name: 'Sign in', exact: true }));
  return user;
}

async function fillCreate(user, email = 'new@example.test') {
  await user.type(screen.getByLabelText('Email address'), email);
  await user.type(screen.getByLabelText('Initial password'), password);
}

describe('auth and account journeys', () => {
  it('signs in as admin, lists accounts, and keeps credentials out of browser storage', async () => {
    render(<App />); await signIn();
    await screen.findByRole('heading', { name: 'Accounts', exact: true });
    expect(await screen.findByText(student.email)).toBeInTheDocument();
    expect(window.localStorage.length).toBe(0); expect(window.sessionStorage.length).toBe(0);
    expect(calls.find(call => call.path.includes('/users?')).headers.Authorization).toBe('Bearer synthetic-memory-token');
  });

  it('shows a safe sign-in failure and clears the submitted password', async () => {
    onRequest = call => call.path.endsWith('/auth/login') ? response({ code: 'UNAUTHENTICATED', message: password }, 401) : null;
    render(<App />); await signIn();
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to sign in');
    expect(screen.getByLabelText('Password', { exact: true })).toHaveValue('');
    expect(screen.queryByText(password)).not.toBeInTheDocument();
  });

  it.each(['STUDENT', 'INSTRUCTOR'])('denies %s an accounts deep link without requesting the directory', async role => {
    identity = { ...student, role }; window.history.replaceState({}, '', '/accounts');
    render(<App />); await signIn();
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    expect(calls.some(call => call.path.includes('/users'))).toBe(false);
    expect(screen.queryByRole('link', { name: 'Accounts' })).not.toBeInTheDocument();
  });

  it('creates an account and refreshes persisted values without retaining its password', async () => {
    render(<App />); const user = await signIn();
    await screen.findByRole('heading', { name: 'Create an account' });
    await fillCreate(user); await user.click(screen.getByRole('button', { name: 'Create account', exact: true }));
    expect(await screen.findByText('Account created successfully.')).toBeInTheDocument();
    expect(await screen.findByText('new@example.test')).toBeInTheDocument();
    expect(screen.getByLabelText('Initial password')).toHaveValue('');
    const body = calls.find(call => call.method === 'POST' && call.path.endsWith('/users')).body;
    expect(body).toEqual({ email: 'new@example.test', password, role: 'STUDENT', active: true });
  });

  it('leaves a duplicate-email error recoverable and never displays server-supplied secrets', async () => {
    onRequest = call => call.method === 'POST' && call.path.endsWith('/users') ? response({ code: 'DUPLICATE_EMAIL', message: password }, 409) : null;
    render(<App />); const user = await signIn();
    await screen.findByRole('heading', { name: 'Create an account' }); await fillCreate(user);
    await user.click(screen.getByRole('button', { name: 'Create account', exact: true }));
    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists.');
    expect(screen.getByRole('button', { name: 'Create account', exact: true })).toBeEnabled();
    expect(screen.queryByText('Account created successfully.')).not.toBeInTheDocument();
  });

  it('updates account status only after a successful server response', async () => {
    render(<App />); const user = await signIn();
    await user.click(await screen.findByRole('button', { name: `Deactivate ${student.email}` }));
    expect(await screen.findByRole('button', { name: `Activate ${student.email}` })).toBeInTheDocument();
    expect(calls.find(call => call.method === 'PATCH').body).toEqual({ active: false });
  });

  it('clears a protected session on 401 and lets the user sign in again', async () => {
    render(<App />); const user = await signIn();
    await screen.findByText(student.email);
    onRequest = call => call.path.includes('/users?') ? response({ code: 'UNAUTHENTICATED' }, 401) : null;
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    expect(await screen.findByRole('heading', { name: 'Sign in to your account' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Your session has ended');
    expect(screen.getByLabelText('Password', { exact: true })).toHaveValue('');
  });

  it('does not restore an authenticated screen when a pending mutation finishes after logout', async () => {
    let resolve;
    onRequest = call => call.method === 'POST' && call.path.endsWith('/users') ? new Promise(done => { resolve = done; }) : null;
    render(<App />); const user = await signIn();
    await screen.findByRole('heading', { name: 'Create an account' }); await fillCreate(user);
    await user.click(screen.getByRole('button', { name: 'Create account', exact: true }));
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    resolve(response({ ...student, id: 'late' }, 201));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in to your account' })).toBeInTheDocument());
    expect(screen.queryByRole('heading', { name: 'Accounts', exact: true })).not.toBeInTheDocument();
  });

  it('preserves the session-expiry explanation when profile access is revoked', async () => {
    identity = student;
    render(<App />); const user = await signIn();
    await screen.findByRole('heading', { name: 'My profile', exact: true });
    onRequest = call => call.path.endsWith('/auth/me') ? response({ code: 'UNAUTHENTICATED' }, 401) : null;
    await user.click(screen.getByRole('button', { name: 'Check account access' }));
    await screen.findByRole('heading', { name: 'Sign in to your account' });
    expect(screen.getByRole('status')).toHaveTextContent('Your session has ended. Please sign in again.');
  });

  it('navigates account pages using the server total', async () => {
    onRequest = call => call.path.includes('/users?') ? response({ items: [student], total: 11 }) : null;
    render(<App />); const user = await signIn();
    await user.click(await screen.findByRole('button', { name: 'Next' }));
    await waitFor(() => expect(calls.some(call => call.path.includes('page=1&size=10'))).toBe(true));
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
  });

  it('treats a non-JSON proxy failure as a safe error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>internal detail</html>', { status: 502 })));
    await expect(api('/auth/me')).rejects.toMatchObject({ status: 502, message: 'The request could not be completed. Please try again.' });
  });
});
