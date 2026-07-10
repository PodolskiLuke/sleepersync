import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock axios so we can inspect how the API layer builds requests without
// making real network calls.
const mockInstance = {
  get: vi.fn(() => Promise.resolve({ data: {} })),
  post: vi.fn(() => Promise.resolve({ data: {} })),
  interceptors: {
    request: { use: vi.fn() },
    response: { use: vi.fn() },
  },
}

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mockInstance),
  },
}))

// Import after the mock is registered.
const { authApi, playersApi, leaguesApi, draftApi, rankingsApi } = await import('./axiosClient')

describe('axiosClient API layer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('authApi', () => {
    it('posts credentials to /auth/register', () => {
      const body = { username: 'a', password: 'b' }
      authApi.register(body)
      expect(mockInstance.post).toHaveBeenCalledWith('/auth/register', body)
    })

    it('posts credentials to /auth/login', () => {
      const body = { username: 'a', password: 'b' }
      authApi.login(body)
      expect(mockInstance.post).toHaveBeenCalledWith('/auth/login', body)
    })

    it('gets the current user from /auth/me', () => {
      authApi.me()
      expect(mockInstance.get).toHaveBeenCalledWith('/auth/me')
    })

    it('links a sleeper account via /auth/link-sleeper', () => {
      authApi.linkSleeper({ sleeperUsername: 'jdoe' })
      expect(mockInstance.post).toHaveBeenCalledWith('/auth/link-sleeper', {
        sleeperUsername: 'jdoe',
      })
    })

    it('selects a league via /auth/select-league', () => {
      authApi.selectLeague({ leagueId: '123' })
      expect(mockInstance.post).toHaveBeenCalledWith('/auth/select-league', {
        leagueId: '123',
      })
    })
  })

  describe('playersApi', () => {
    it('URL-encodes the search term', () => {
      playersApi.search('Ja Morant')
      expect(mockInstance.get).toHaveBeenCalledWith('/players/search?name=Ja%20Morant')
    })

    it('fetches a player by id', () => {
      playersApi.getById(42)
      expect(mockInstance.get).toHaveBeenCalledWith('/players/42')
    })
  })

  describe('leaguesApi', () => {
    it('builds the matchups URL with league id and week', () => {
      leaguesApi.getMatchups('L1', 5)
      expect(mockInstance.get).toHaveBeenCalledWith('/leagues/L1/matchups/5')
    })
  })

  describe('draftApi', () => {
    it('encodes the username when resolving a user', () => {
      draftApi.resolveUser('john doe')
      expect(mockInstance.get).toHaveBeenCalledWith('/drafts/resolve/john%20doe')
    })

    it('passes sleeperUserId as a query param for my-picks', () => {
      draftApi.getMyPicks('D1', 'U9')
      expect(mockInstance.get).toHaveBeenCalledWith('/drafts/D1/my-picks', {
        params: { sleeperUserId: 'U9' },
      })
    })

    it('defaults best-available limit to 15', () => {
      draftApi.getBestAvailable('D1')
      expect(mockInstance.get).toHaveBeenCalledWith('/drafts/D1/best-available', {
        params: { limit: 15 },
      })
    })

    it('honours an explicit best-available limit', () => {
      draftApi.getBestAvailable('D1', 50)
      expect(mockInstance.get).toHaveBeenCalledWith('/drafts/D1/best-available', {
        params: { limit: 50 },
      })
    })
  })

  describe('rankingsApi', () => {
    it('defaults the remaining rankings limit to 20', () => {
      rankingsApi.getRemainingForDraft('D1')
      expect(mockInstance.get).toHaveBeenCalledWith('/rankings/draft/D1/remaining', {
        params: { limit: 20 },
      })
    })
  })
})
