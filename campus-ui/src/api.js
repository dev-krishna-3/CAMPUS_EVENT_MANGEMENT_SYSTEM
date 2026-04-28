// ==================== API MODULE ====================
const API = '/api';

export async function request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${API}${endpoint}`;
    try {
        const res = await fetch(url, options);
        const text = await res.text();
        const data = text ? JSON.parse(text) : {};
        if (!res.ok) throw new Error(data.message || data.error || 'Request failed');
        return data;
    } catch (err) {
        if (err.message?.includes('fetch')) {
            toast('Cannot connect to backend server', 'error');
        } else {
            toast(err.message, 'error');
        }
        console.error('[API]', url, err);
        return null;
    }
}

// ==================== TOAST ====================
export function toast(msg, type = 'success') {
    document.querySelectorAll('.toast').forEach(t => t.remove());
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = msg;
    document.body.appendChild(el);
    requestAnimationFrame(() => setTimeout(() => el.classList.add('show'), 30));
    setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 400); }, 3000);
}

// ==================== AUTH ====================
export function getUser() {
    try { return JSON.parse(localStorage.getItem('user')); } catch { return null; }
}

export function requireAuth() {
    if (!getUser()) { location.href = '/login.html'; return null; }
    return getUser();
}

export function logout() {
    localStorage.removeItem('user');
    toast('Logged out');
    setTimeout(() => location.href = '/login.html', 400);
}

export async function login(email, password) {
    const res = await request('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });
    if (!res?.success) return null;

    // Get full user data
    const users = await request('/users');
    const user = users?.find(u => u.email.toLowerCase() === email.toLowerCase());
    if (user) {
        user.role = user.role.toLowerCase().trim();
        localStorage.setItem('user', JSON.stringify(user));
    }
    return user || res;
}

export async function signup(data) {
    data.role = (data.role || 'student').toLowerCase().trim();
    return request('/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
}

export function redirectByRole(role) {
    const r = (role || '').toLowerCase();
    if (r === 'admin') location.href = '/admin.html';
    else if (r.includes('super')) location.href = '/superadmin.html';
    else location.href = '/student.html';
}

// ==================== EVENTS ====================
export async function loadEvents(container, opts = {}) {
    if (!container) return [];
    container.innerHTML = '<p class="loading">Loading events...</p>';

    let url = '/events?';
    if (opts.categoryId) url += `categoryId=${opts.categoryId}&`;
    if (opts.query) url += `query=${encodeURIComponent(opts.query)}&`;
    if (opts.sort) url += `sort=${opts.sort}`;

    const events = await request(url);
    if (!events) { container.innerHTML = '<p class="empty">Could not load events.</p>'; return []; }
    return events;
}

export async function loadCategories() {
    return await request('/categories') || [];
}

export async function loadClubs() {
    return await request('/clubs') || [];
}

export function populateSelect(sel, items, labelKey = 'name', valueKey = 'id', placeholder = '') {
    if (!sel) return;
    sel.innerHTML = placeholder ? `<option value="">${placeholder}</option>` : '';
    (items || []).forEach(item => {
        sel.innerHTML += `<option value="${item[valueKey]}">${item[labelKey]}</option>`;
    });
}

export function populateEventSelects(events, ids) {
    ids.forEach(id => {
        const sel = document.getElementById(id);
        if (sel) populateSelect(sel, events, 'title', 'id', 'Select Event');
    });
}

export function formatDate(d) {
    if (!d) return 'TBA';
    try { return new Date(d.replace(' ', 'T')).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }); }
    catch { return d; }
}

export function fmtSqlDate(s) {
    if (!s) return '';
    let f = s.replace('T', ' ').trim();
    // Ensure it has seconds
    if (f.length === 16) f += ':00';
    // If user types weird things, attempt to force it to YYYY-MM-DD HH:MM:SS
    if (f.length < 19 && f.length > 0) {
        return f + ':00.000000'.substring(0, 19 - f.length);
    }
    return f.substring(0, 19);
}
