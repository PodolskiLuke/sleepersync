import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

let mockUser = { displayName: 'Jane', sleeperUsername: 'jane', activeLeagueId: 'L1' }

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: mockUser }),
}))

vi.mock('../api/axiosClient', () => ({
  leaguesApi: {
    getLeague: vi.fn(),
    getRosters: vi.fn(),
  },
}))

import { leaguesApi } from '../api/axiosClient'
import Dashboard from './Dashboard'

const LEAGUE = {
  name: 'Dynasty Warriors',
  total_rosters: 12,
  season: '2026',
  season_type: 'regular',
  status: 'in_season',
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>
  )
}

describe('Dashboard page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUser = { displayName: 'Jane', sleeperUsername: 'jane', activeLeagueId: 'L1' }
  })

  it('greets the user by display name', () => {
    leaguesApi.getLeague.mockResolvedValue({ data: LEAGUE })
    leaguesApi.getRosters.mockResolvedValue({ data: [] })
    renderDashboard()
    expect(screen.getByText(/Welcome back, Jane/)).toBeInTheDocument()
  })

  it('renders the active league and roster count once loaded', async () => {
    leaguesApi.getLeague.mockResolvedValue({ data: LEAGUE })
    leaguesApi.getRosters.mockResolvedValue({ data: [{}, {}, {}] })
    renderDashboard()

    expect(await screen.findByText('Dynasty Warriors')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('always renders the tools, with Draft Helper available', async () => {
    leaguesApi.getLeague.mockResolvedValue({ data: LEAGUE })
    leaguesApi.getRosters.mockResolvedValue({ data: [] })
    renderDashboard()

    const draftHelper = await screen.findByText('Draft Helper')
    // The available tool links to /draft-helper.
    expect(draftHelper.closest('a')).toHaveAttribute('href', '/draft-helper')
    // Unavailable tools show a "Coming soon" badge.
    expect(screen.getAllByText('Coming soon').length).toBeGreaterThan(0)
  })

  it('does not fetch a league when the user has no active league', () => {
    mockUser = { displayName: 'Jane', sleeperUsername: 'jane', activeLeagueId: null }
    renderDashboard()
    expect(leaguesApi.getLeague).not.toHaveBeenCalled()
  })
})
