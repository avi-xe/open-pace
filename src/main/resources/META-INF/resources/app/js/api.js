/**
 * Open Pace API Client
 *
 * Wraps fetch() with Basic Auth, error handling, and endpoint methods.
 * Credentials stored in sessionStorage under 'credentials' as "username:password".
 */
const OpenPaceApi = {

  /**
   * Get stored credentials for Basic Auth.
   */
  getCredentials() {
    return sessionStorage.getItem('credentials');
  },

  /**
   * Store credentials after login.
   */
  setCredentials(username, password) {
    sessionStorage.setItem('credentials', `${username}:${password}`);
    sessionStorage.setItem('username', username);
  },

  /**
   * Clear credentials on logout.
   */
  clearCredentials() {
    sessionStorage.removeItem('credentials');
    sessionStorage.removeItem('username');
  },

  /**
   * Get current username.
   */
  getUsername() {
    return sessionStorage.getItem('username');
  },

  /**
   * Check if user is authenticated.
   */
  isAuthenticated() {
    return !!this.getCredentials();
  },

  /**
   * Internal fetch wrapper with auth and error handling.
   */
  async request(url, options = {}) {
    const headers = options.headers || {};
    headers['Accept'] = headers['Accept'] || 'application/json';

    const creds = this.getCredentials();
    if (creds && !options.noAuth) {
      headers['Authorization'] = 'Basic ' + btoa(creds);
    }

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, { ...options, headers });

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }));
      throw new ApiError(response.status, error.error || 'Error', error.message || 'Request failed');
    }

    return response.json();
  },

  // === Auth ===

  async register(username, password, email) {
    return this.request('/api/auth/register', {
      method: 'POST',
      body: { username, password, email },
      noAuth: true
    });
  },

  async me() {
    return this.request('/api/auth/me');
  },

  // === Profile ===

  async getProfile(username) {
    return this.request(`/api/users/${username}/profile`, { noAuth: true });
  },

  async getFollowers(username) {
    return this.request(`/api/users/${username}/followers`, { noAuth: true });
  },

  async getFollowing(username) {
    return this.request(`/api/users/${username}/following`, { noAuth: true });
  },

  // === Feed ===

  async getFeed() {
    return this.request('/api/feed', { noAuth: true });
  },

  // === Activities ===

  async getActivityPubJson(activityId) {
    const response = await fetch(`/activities/${activityId}`, {
      headers: { 'Accept': 'application/activity+json' }
    });
    return response.json();
  },

  // === Federation ===

  async getInstances() {
    return this.request('/api/federation/instances', { noAuth: true });
  },

  async followRemote(actorUrl, username) {
    return this.request('/api/federation/follow', {
      method: 'POST',
      body: { actorUrl, username }
    });
  },

  // === Segments & Leaderboards ===

  async getSegments() {
    return this.request('/api/segments', { noAuth: true });
  },

  async getLeaderboard(segmentId) {
    return this.request(`/api/segments/${segmentId}/leaderboard`, { noAuth: true });
  },

  async getOverallLeaderboard() {
    return this.request('/api/leaderboards/overall', { noAuth: true });
  },

  // === Outbox (C2S) ===

  async postToOutbox(username, activity) {
    return this.request(`/users/${username}/outbox`, {
      method: 'POST',
      body: activity,
      headers: { 'Content-Type': 'application/activity+json' }
    });
  },

  // === Map & Export ===

  getMapUrl(activityId) {
    return `/api/activities/${activityId}/map.png`;
  },

  getGpxExportUrl(activityId) {
    return `/api/activities/${activityId}/export/gpx`;
  }
};

/**
 * Custom API error with status, error code, and message.
 */
class ApiError extends Error {
  constructor(status, errorCode, message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }
}
