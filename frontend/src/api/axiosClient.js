import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Attach the JWT token to every request automatically
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// If the server returns 401 (expired/invalid token), log the user out
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// -------------------------------------------------------------------------
// Auth endpoints
// -------------------------------------------------------------------------

export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  me: () => api.get('/auth/me'),
  linkSleeper: (data) => api.post('/auth/link-sleeper', data),
  getLeagues: () => api.get('/auth/leagues'),
  selectLeague: (data) => api.post('/auth/select-league', data),
}

// -------------------------------------------------------------------------
// Players
// -------------------------------------------------------------------------

export const playersApi = {
  getAll: () => api.get('/players'),
  getActive: () => api.get('/players/active'),
  search: (name) => api.get(`/players/search?name=${encodeURIComponent(name)}`),
  getById: (id) => api.get(`/players/${id}`),
  syncAll: () => api.post('/players/sync'),
}

// -------------------------------------------------------------------------
// Leagues
// -------------------------------------------------------------------------

export const leaguesApi = {
  getLeague: (leagueId) => api.get(`/leagues/${leagueId}`),
  getRosters: (leagueId) => api.get(`/leagues/${leagueId}/rosters`),
  getMatchups: (leagueId, week) => api.get(`/leagues/${leagueId}/matchups/${week}`),
}

// -------------------------------------------------------------------------
// Draft Helper
// -------------------------------------------------------------------------

export const draftApi = {
  resolveUser: (username) => api.get(`/drafts/resolve/${encodeURIComponent(username)}`),
  getDraft: (draftId) => api.get(`/drafts/${draftId}`),
  getPicks: (draftId) => api.get(`/drafts/${draftId}/picks`),
  getMyPicks: (draftId, sleeperUserId) =>
    api.get(`/drafts/${draftId}/my-picks`, { params: { sleeperUserId } }),
  getBestAvailable: (draftId, limit = 15) =>
    api.get(`/drafts/${draftId}/best-available`, { params: { limit } }),
}

// -------------------------------------------------------------------------
// Rankings
// -------------------------------------------------------------------------

export const rankingsApi = {
  scrape: () => api.get('/rankings/scrape'),
  getAllRankings: () => api.get('/rankings/all'),
  getRemainingForDraft: (draftId, limit = 20) =>
    api.get(`/rankings/draft/${draftId}/remaining`, { params: { limit } }),
}

export default api
