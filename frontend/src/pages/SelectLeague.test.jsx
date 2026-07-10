import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

const mockNavigate = vi.fn()
const mockSelectLeague = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { sleeperUsername: 'jane' },
    selectLeague: mockSelectLeague,
  }),
}))

vi.mock('../api/axiosClient', () => ({
  authApi: { getLeagues: vi.fn() },
}))

import { authApi } from '../api/axiosClient'
import SelectLeague from './SelectLeague'

const LEAGUES = [
  {
    league_id: 'L1',
    name: 'Dynasty Warriors',
    total_rosters: 12,
    season: '2026',
    season_type: 'regular',
    status: 'in_season',
  },
  {
    league_id: 'L2',
    name: 'Redraft Rumble',
    total_rosters: 10,
    season: '2026',
    season_type: 'regular',
    status: 'drafting',
  },
]

function renderSelectLeague() {
  return render(
    <MemoryRouter>
      <SelectLeague />
    </MemoryRouter>
  )
}

describe('SelectLeague page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the fetched leagues', async () => {
    authApi.getLeagues.mockResolvedValue({ data: LEAGUES })
    renderSelectLeague()

    expect(await screen.findByText('Dynasty Warriors')).toBeInTheDocument()
    expect(screen.getByText('Redraft Rumble')).toBeInTheDocument()
  })

  it('shows an empty state when there are no leagues', async () => {
    authApi.getLeagues.mockResolvedValue({ data: [] })
    renderSelectLeague()

    expect(
      await screen.findByText('No NBA leagues found for this Sleeper account.')
    ).toBeInTheDocument()
  })

  it('shows an error message when the fetch fails', async () => {
    authApi.getLeagues.mockRejectedValue(new Error('down'))
    renderSelectLeague()

    expect(
      await screen.findByText('Failed to load leagues. Please try again.')
    ).toBeInTheDocument()
  })

  it('selects a league and navigates to the dashboard', async () => {
    authApi.getLeagues.mockResolvedValue({ data: LEAGUES })
    mockSelectLeague.mockResolvedValue({})
    renderSelectLeague()

    const firstLeague = await screen.findByText('Dynasty Warriors')
    await userEvent.click(firstLeague)

    await waitFor(() => expect(mockSelectLeague).toHaveBeenCalledWith('L1'))
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard')
  })
})
