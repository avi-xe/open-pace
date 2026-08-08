/**
 * Federation Utilities
 *
 * WebFinger resolution, instance detection, and ActivityPub inspection.
 */
const Federation = {

  /**
   * Resolve a WebFinger query for @user@domain.
   * Returns the actor profile URL or null if not found.
   */
  async resolveWebFinger(user, domain) {
    try {
      const url = `/api/federation/webfinger?resource=acct:${user}@${domain}`;
      const response = await fetch(url, {
        headers: { 'Accept': 'application/jrd+json' }
      });

      if (!response.ok) return null;

      const jrd = await response.json();
      const links = jrd.links || [];
      const selfLink = links.find(l => l.rel === 'self');
      return selfLink ? selfLink.href : null;
    } catch (e) {
      console.warn('WebFinger resolution failed:', e);
      return null;
    }
  },

  /**
   * Fetch an ActivityPub actor profile from any URL.
   * Proxies through backend to avoid CORS issues with remote servers.
   */
  async fetchActorProfile(actorUrl) {
    try {
      // Use backend proxy for all actor fetches (avoids CORS)
      const proxyUrl = `/api/federation/actor?url=${encodeURIComponent(actorUrl)}`;
      const response = await fetch(proxyUrl, {
        headers: { 'Accept': 'application/json' }
      });

      if (!response.ok) return null;
      return await response.json();
    } catch (e) {
      console.warn('Failed to fetch actor profile:', e);
      return null;
    }
  },

  /**
   * Parse @user@domain string into components.
   * Returns { user, domain } or null if invalid.
   */
  parseWebFinger(input) {
    const trimmed = input.trim();
    if (!trimmed.startsWith('@')) return null;

    const withoutAt = trimmed.substring(1);
    const parts = withoutAt.split('@');
    if (parts.length !== 2 || !parts[0] || !parts[1]) return null;

    return { user: parts[0], domain: parts[1] };
  },

  /**
   * Extract domain from an ActivityPub actor URL.
   * e.g., "https://mastodon.social/users/alice" → "mastodon.social"
   */
  getInstanceDomain(actorUrl) {
    try {
      const url = new URL(actorUrl);
      return url.hostname;
    } catch {
      return null;
    }
  },

  /**
   * Check if a URL belongs to the local instance.
   */
  isLocalInstance(actorUrl) {
    const domain = this.getInstanceDomain(actorUrl);
    if (!domain) return true;
    return domain === 'localhost' || domain === '127.0.0.1' || domain.includes('localhost');
  },

  /**
   * Get display-friendly instance name from domain.
   */
  getInstanceDisplayName(domain) {
    if (!domain || domain === 'localhost' || domain === '127.0.0.1') {
      return 'This Instance';
    }
    return domain;
  },

  /**
   * Format ActivityPub JSON for display with syntax highlighting.
   * Returns an HTML string with colored JSON.
   */
  formatActivityPubJson(json) {
    const str = JSON.stringify(json, null, 2);
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"([^"]+)"(?=\s*:)/g, '<span class="json-key">"$1"</span>')
      .replace(/:\s*"([^"]*)"/g, ': <span class="json-string">"$1"</span>')
      .replace(/:\s*(true|false)/g, ': <span class="json-bool">$1</span>')
      .replace(/:\s*(\d+)/g, ': <span class="json-number">$1</span>');
  },

  /**
   * Build the federation flow explanation HTML.
   */
  buildFlowDiagram() {
    return `
      <div class="federation-flow">
        <div class="flow-step">
          <div class="flow-icon">📝</div>
          <div class="flow-label">You Create</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="flow-icon">📦</div>
          <div class="flow-label">Outbox</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="flow-icon">📡</div>
          <div class="flow-label">Followers' Inboxes</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="flow-icon">🌐</div>
          <div class="flow-label">Remote Instances</div>
        </div>
      </div>
    `;
  }
};
