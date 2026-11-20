import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api.js';

const safeError = error => error instanceof ApiError ? error.message : 'Unable to connect. Please try again.';
const roleLabel = role => ({ ADMIN: 'Admin', INSTRUCTOR: 'Instructor', STUDENT: 'Student' })[role] || role;

function useNavigation() {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const changed = () => setPath(window.location.pathname);
    window.addEventListener('popstate', changed);
    return () => window.removeEventListener('popstate', changed);
  }, []);
  const navigate = useCallback((next, replace = false) => {
    window.history[replace ? 'replaceState' : 'pushState']({}, '', next);
    setPath(next);
  }, []);
  return [path, navigate];
}

function Brand() {
  return <div className="brand"><span className="brand-mark" aria-hidden="true">Q</span><span>QuizMaster<span className="brand-sub">EXAMINATION MANAGEMENT</span></span></div>;
}

function Login({ signIn, notice }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const credentials = { email: data.get('email'), password: data.get('password') };
    form.elements.password.value = '';
    setBusy(true); setError('');
    try { await signIn(credentials); }
    catch (failure) { setError(safeError(failure)); }
    finally { setBusy(false); }
  }
  return <div className="login-layout">
    <aside className="welcome-panel">
      <Brand />
      <div className="welcome-copy"><p className="eyebrow">A CLEAR START</p><h1>Your next chapter<br />starts here.</h1><p>One place to access your account<br />and manage your examination workspace.</p>
        <div className="welcome-art" aria-hidden="true"><div className="art-sheet"><span className="art-check">✓</span><i /><i /><i /><div className="art-line" /><i /><i /></div><span className="art-circle" /></div>
      </div>
      <p className="panel-footer">QuizMaster · Account workspace</p>
    </aside>
    <main className="login-main"><div className="login-form-wrap"><p className="eyebrow">WELCOME BACK</p><h2>Sign in to your account</h2><p className="muted">Use the credentials provided by your administrator.</p>
      {notice && <p className="notice" role="status">{notice}</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <form onSubmit={submit} aria-label="Sign in">
        <fieldset disabled={busy}><label htmlFor="login-email">Email address</label><input id="login-email" name="email" type="email" autoComplete="username" maxLength={254} required placeholder="you@example.com" />
          <label htmlFor="login-password">Password</label><input id="login-password" name="password" type="password" autoComplete="current-password" minLength={12} maxLength={128} required />
          <button className="primary wide" type="submit">{busy ? 'Signing in…' : 'Sign in'}</button>
        </fieldset>
      </form><div className="login-help"><span aria-hidden="true">◇</span><p>Need access?<br /><span>Contact your administrator to create or reactivate an account.</span></p></div>
    </div><p className="login-footer">A focused space for better examination management.</p></main>
  </div>;
}

function Accounts({ request, currentUser }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [revision, setRevision] = useState(0);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [creating, setCreating] = useState(false);
  const [updating, setUpdating] = useState('');
  const lifecycle = useRef(null);
  useEffect(() => {
    lifecycle.current = new AbortController();
    return () => lifecycle.current.abort();
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setData(null); setLoadError('');
    request(`/users?page=${page}&size=10`, { signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => { if (!controller.signal.aborted) setLoadError(safeError(failure)); });
    return () => controller.abort();
  }, [page, revision, request]);

  async function create(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const body = { email: values.get('email'), password: values.get('password'), role: values.get('role'), active: values.get('active') === 'on' };
    form.elements.password.value = '';
    const signal = lifecycle.current.signal;
    setCreating(true); setError(''); setSuccess('');
    try {
      await request('/users', { method: 'POST', body, signal });
      if (!signal.aborted) {
        form.reset(); setSuccess('Account created successfully.');
        setPage(0); setRevision(value => value + 1);
      }
    } catch (failure) { if (!signal.aborted) setError(safeError(failure)); }
    finally { if (!signal.aborted) setCreating(false); }
  }

  async function changeStatus(account) {
    const signal = lifecycle.current.signal;
    setUpdating(account.id); setError(''); setSuccess('');
    try {
      await request(`/users/${account.id}/status`, { method: 'PATCH', body: { active: !account.active }, signal });
      if (account.id === currentUser.id) await request('/auth/me', { signal });
      if (!signal.aborted) {
        setSuccess(`Account ${account.active ? 'deactivated' : 'activated'} successfully.`);
        setRevision(value => value + 1);
      }
    } catch (failure) { if (!signal.aborted) setError(safeError(failure)); }
    finally { if (!signal.aborted) setUpdating(''); }
  }

  return <><header className="page-heading"><div><p className="eyebrow">WORKSPACE / ADMINISTRATION</p><h1>Accounts</h1><p className="muted">Manage who can access your examination workspace.</p></div><span className="access-label">Admin access</span></header>
    <div className="accounts-grid"><section className="card account-list" aria-labelledby="directory-title"><div className="card-heading"><div><h2 id="directory-title">Account directory</h2><p className="muted">{data ? `${data.total} ${data.total === 1 ? 'account' : 'accounts'} in your workspace` : 'Loading your accounts…'}</p></div><button className="quiet" onClick={() => setRevision(value => value + 1)}>Refresh</button></div>
      {loadError ? <p className="error" role="alert">{loadError}</p> : !data ? <p role="status" className="loading">Loading accounts…</p> : <>
        {data.items.length === 0 ? <p className="loading">No accounts on this page.</p> : <div className="table-scroll"><table><thead><tr><th>Email address</th><th>Role</th><th>Status</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{data.items.map(account => <tr key={account.id}><td className="email-cell">{account.email}{account.id === currentUser.id && <span className="you">You</span>}</td><td>{roleLabel(account.role)}</td><td><span className={`badge ${account.active ? 'active' : 'inactive'}`}>{account.active ? 'Active' : 'Inactive'}</span></td><td><button className="text-button" disabled={Boolean(updating)} aria-label={`${account.active ? 'Deactivate' : 'Activate'} ${account.email}`} onClick={() => changeStatus(account)}>{updating === account.id ? 'Updating…' : account.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}</tbody></table></div>}
        <div className="pagination"><span>Page {page + 1} of {Math.max(1, Math.ceil(data.total / 10))}</span><div><button className="quiet" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><button className="quiet" disabled={(page + 1) * 10 >= data.total} onClick={() => setPage(value => value + 1)}>Next</button></div></div>
      </>}
    </section><section className="card create-account" aria-labelledby="create-title"><div className="card-heading"><div><h2 id="create-title">Create an account</h2><p className="muted">Give someone access to QuizMaster.</p></div></div>
      <form onSubmit={create} aria-label="Create account"><fieldset disabled={creating}>
        <label htmlFor="new-email">Email address</label><input id="new-email" name="email" type="email" autoComplete="off" maxLength={254} required placeholder="name@example.com" />
        <label htmlFor="new-password">Initial password</label><input id="new-password" name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required aria-describedby="password-hint" /><p className="field-hint" id="password-hint">Use 12–128 characters.</p>
        <label htmlFor="new-role">Role</label><select id="new-role" name="role" defaultValue="STUDENT"><option value="STUDENT">Student</option><option value="INSTRUCTOR">Instructor</option><option value="ADMIN">Admin</option></select>
        <label className="checkbox"><input name="active" type="checkbox" defaultChecked />Active account</label>
        <button className="primary wide" type="submit">{creating ? 'Creating…' : 'Create account'}</button>
      </fieldset></form>
    </section></div>
    {error && <p className="error" role="alert">{error}</p>}{success && <p className="success" role="status">{success}</p>}
  </>;
}

export default function App() {
  const [session, setSession] = useState(null);
  const [notice, setNotice] = useState('');
  const generation = useRef(0);
  const [path, navigate] = useNavigation();
  const logout = useCallback((message = 'You have signed out.') => {
    generation.current += 1; setSession(null); setNotice(message); navigate('/', true);
  }, [navigate]);
  async function signIn(credentials) {
    const version = ++generation.current;
    const result = await api('/auth/login', { method: 'POST', body: credentials });
    const user = await api('/auth/me', { token: result.accessToken });
    if (version !== generation.current) return;
    setSession({ token: result.accessToken, user, version }); setNotice('');
    // Respect explicit deep links; default admins to their working directory.
    if (path === '/') navigate(user.role === 'ADMIN' ? '/accounts' : '/', true);
  }
  const request = useCallback(async (resource, options = {}) => {
    try { return await api(resource, { ...options, token: session?.token }); }
    catch (error) {
      if (error.status === 401 && session?.version === generation.current) logout('Your session has ended. Please sign in again.');
      throw error;
    }
  }, [session, logout]);

  if (!session) return <Login signIn={signIn} notice={notice} />;
  const { user } = session;
  const isAdmin = user.role === 'ADMIN';
  function link(event, next) { if (event.button === 0 && !event.ctrlKey && !event.metaKey) { event.preventDefault(); navigate(next); } }
  return <div className="workspace"><a className="skip-link" href="#main">Skip to content</a><aside className="sidebar"><Brand /><p className="nav-caption">WORKSPACE</p><nav aria-label="Workspace">{isAdmin && <a href="/accounts" className={path === '/accounts' ? 'selected' : ''} aria-current={path === '/accounts' ? 'page' : undefined} onClick={event => link(event, '/accounts')}><span aria-hidden="true">▦</span>Accounts</a>}<a href="/" className={path === '/' ? 'selected' : ''} aria-current={path === '/' ? 'page' : undefined} onClick={event => link(event, '/')}><span aria-hidden="true">○</span>My profile</a></nav><div className="sidebar-bottom"><span className="avatar" aria-hidden="true">{user.email[0].toUpperCase()}</span><div><strong>{roleLabel(user.role)}</strong><span className="sidebar-email">{user.email}</span></div></div></aside>
    <div className="workspace-body"><header className="topbar"><span>Account workspace</span><button className="quiet" onClick={() => logout()}>Sign out</button></header><main id="main" tabIndex={-1} className="main-content">
      {path === '/accounts' ? isAdmin ? <Accounts key={session.version} request={request} currentUser={user} /> : <div className="card denied"><p className="eyebrow">RESTRICTED AREA</p><h1>Access denied</h1><p>Only administrators can manage accounts.</p><button className="primary" onClick={() => navigate('/')}>Go to my profile</button></div> : path === '/' ? <><header className="page-heading"><div><p className="eyebrow">WORKSPACE / MY PROFILE</p><h1>My profile</h1><p className="muted">Your account details and access.</p></div></header><section className="card profile"><span className="profile-avatar" aria-hidden="true">{user.email[0].toUpperCase()}</span><h2>{user.email}</h2><dl><div><dt>Role</dt><dd>{roleLabel(user.role)}</dd></div><div><dt>Account status</dt><dd><span className="badge active">Active</span></dd></div></dl><button className="quiet" onClick={() => request('/auth/me').catch(error => { if (error.status !== 401 && session.version === generation.current) setNotice(safeError(error)); })}>Check account access</button>{notice && <p role="status">{notice}</p>}</section></> : <section className="card denied"><h1>Page not found</h1><button className="primary" onClick={() => navigate('/')}>Go to my profile</button></section>}
    </main><footer className="workspace-footer">QuizMaster<span>Account management</span></footer></div>
  </div>;
}
