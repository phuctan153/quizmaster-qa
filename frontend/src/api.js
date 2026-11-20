const messages = {
  UNAUTHENTICATED: 'Unable to sign in. Check your credentials and account status.',
  FORBIDDEN: 'You do not have permission to perform this action.',
  DUPLICATE_EMAIL: 'An account with this email already exists.',
  VALIDATION_ERROR: 'Check the entered values and try again.',
  NOT_FOUND: 'This account could not be found. Refresh the list and try again.',
};

export class ApiError extends Error {
  constructor(status, code) {
    super(messages[code] || 'The request could not be completed. Please try again.');
    this.status = status;
  }
}

export async function api(path, { token, method = 'GET', body, signal } = {}) {
  const response = await fetch(`/api/v1${path}`, {
    method, signal, cache: 'no-store',
    headers: { ...(body !== undefined && { 'Content-Type': 'application/json' }), ...(token && { Authorization: `Bearer ${token}` }) },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, data?.code);
  if (data === null) throw new ApiError(502);
  return data;
}
