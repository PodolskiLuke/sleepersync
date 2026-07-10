import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

vi.mock('../api/axiosClient', () => ({
  draftApi: {
    resolveUser: vi.fn(),
    getDraft: vi.fn(),
    getPicks: vi.fn(),
    getMyPicks: vi.fn(),
    getBestAvailable: vi.fn(),
  },
  leaguesApi: { getLeague: vi.fn() },
  playersApi: { getAll: vi.fn() },
  rankingsApi: { getRemainingForDraft: vi.fn() },
}))

import { draftApi, leaguesApi, playersApi, rankingsApi } from '../api/axiosClient'
import DraftHelper from './DraftHelper'

const STORAGE_KEY = 'draftHelperSession'

// Wires up every board API call so a successful connect doesn't leave
// unhandled promise rejections when the polling effect runs.
function stubBoardApis() {
  draftApi.getPicks.mockResolvedValue({ data: [] })
  draftApi.getMyPicks.mockResolvedValue({ data: [] })
  draftApi.getBestAvailable.mockResolvedValue({ data: { overall: [], byPosition: {} } })
  rankingsApi.getRemainingForDraft.mockResolvedValue({ data: { overall: [] } })
  playersApi.getAll.mockResolvedValue({ data: [] })
}

describe('DraftHelper setup screen', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('renders the setup form', () => {
    render(<DraftHelper />)
    expect(screen.getByLabelText('Sleeper Username')).toBeInTheDocument()
    expect(screen.getByLabelText('League Draft ID')).toBeInTheDocument()
  })

  it('disables Connect until both fields are filled', async () => {
    render(<DraftHelper />)
    const button = screen.getByRole('button', { name: 'Connect to Draft' })
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoops')
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    expect(button).toBeEnabled()
  })

  it('restores the username and draft ID from localStorage', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ username: 'savedUser', draftId: '999' })
    )
    render(<DraftHelper />)
    expect(screen.getByLabelText('Sleeper Username')).toHaveValue('savedUser')
    expect(screen.getByLabelText('League Draft ID')).toHaveValue('999')
  })

  it('shows a setup error when the connection fails', async () => {
    draftApi.resolveUser.mockRejectedValue({ response: { data: { message: 'No user' } } })
    draftApi.getDraft.mockRejectedValue(new Error('bad draft'))
    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'ghost')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    expect(await screen.findByText('No user')).toBeInTheDocument()
  })

  it('connects successfully and shows the live board header', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({ data: { status: 'drafting', settings: {} } })
    stubBoardApis()
    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    await waitFor(() =>
      expect(draftApi.resolveUser).toHaveBeenCalledWith('hoopsdynasty')
    )
    // The board header renders the connected username.
    expect(await screen.findByText('@hoopsdynasty')).toBeInTheDocument()
    expect(screen.getByText('How this board works')).toBeInTheDocument()
    // The session is persisted for next time.
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY))
    expect(saved).toMatchObject({ userId: 'U1', username: 'hoopsdynasty', draftId: '123' })
  })

  it('shows my team grouped into starters and bench when roster positions are available', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({
      data: [
        { pickNo: 1, round: 1, draftSlot: 1, fullName: 'Cade Cunningham', position: 'PG', team: 'DET' },
        { pickNo: 2, round: 2, draftSlot: 1, fullName: 'Desmond Bane', position: 'SG', team: 'ORL' },
        { pickNo: 3, round: 3, draftSlot: 1, fullName: 'Jaren Jackson Jr.', position: 'PF/C', team: 'MEM' },
      ],
    })
    draftApi.getBestAvailable.mockResolvedValue({ data: { overall: [], byPosition: {} } })
    rankingsApi.getRemainingForDraft.mockResolvedValue({ data: { overall: [], byPosition: {}, rookies: [] } })
    playersApi.getAll.mockResolvedValue({ data: [] })
    leaguesApi.getLeague.mockResolvedValue({
      data: { roster_positions: ['PG', 'SG', 'UTIL', 'BN'] },
    })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    expect(await screen.findByText('Starting Lineup')).toBeInTheDocument()
    await waitFor(() => expect(leaguesApi.getLeague).toHaveBeenCalledWith('L1'))
    expect(screen.getByText('Bench')).toBeInTheDocument()
    const pgRow = screen.getByText('Cade Cunningham').closest('li')
    const sgRow = screen.getByText('Desmond Bane').closest('li')
    const utilRow = screen.getByText('Jaren Jackson Jr.').closest('li')

    expect(pgRow).toHaveTextContent('PG')
    expect(sgRow).toHaveTextContent('SG')
    expect(utilRow).toHaveTextContent('UTIL')
    expect(screen.getByText('3/3 filled')).toBeInTheDocument()
    expect(screen.getByText('No bench players yet.')).toBeInTheDocument()
  })

  it('falls back to an estimated lineup layout when league roster positions are unavailable', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({
      data: [
        { pickNo: 1, round: 1, draftSlot: 1, fullName: 'Cade Cunningham', position: 'PG', team: 'DET' },
        { pickNo: 2, round: 2, draftSlot: 1, fullName: 'Desmond Bane', position: 'SG', team: 'ORL' },
      ],
    })
    draftApi.getBestAvailable.mockResolvedValue({ data: { overall: [], byPosition: {} } })
    rankingsApi.getRemainingForDraft.mockResolvedValue({ data: { overall: [], byPosition: {}, rookies: [] } })
    playersApi.getAll.mockResolvedValue({ data: [] })
    leaguesApi.getLeague.mockResolvedValue({ data: {} })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    expect(await screen.findByText('Starting Lineup')).toBeInTheDocument()
    expect(screen.getByText('Estimated lineup layout')).toBeInTheDocument()
    expect(screen.getByText('Cade Cunningham')).toBeInTheDocument()
    expect(screen.queryByText('G')).not.toBeInTheDocument()
  })

  it('fills specific starter slots before util slots when league slot order is mixed', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({
      data: [
        { pickNo: 1, round: 1, draftSlot: 1, fullName: 'VJ Edgecombe', position: 'SG', team: 'PHI' },
        { pickNo: 2, round: 2, draftSlot: 1, fullName: 'Caris LeVert', position: 'SG', team: 'ATL' },
      ],
    })
    draftApi.getBestAvailable.mockResolvedValue({ data: { overall: [], byPosition: {} } })
    rankingsApi.getRemainingForDraft.mockResolvedValue({ data: { overall: [], byPosition: {}, rookies: [] } })
    playersApi.getAll.mockResolvedValue({ data: [] })
    leaguesApi.getLeague.mockResolvedValue({
      data: { roster_positions: ['UTIL', 'SG', 'BN'] },
    })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    expect(await screen.findByText('Starting Lineup')).toBeInTheDocument()
    const sgRow = screen.getByText('VJ Edgecombe').closest('li')
    const utilRow = screen.getByText('Caris LeVert').closest('li')

    expect(sgRow).toHaveTextContent('VJ Edgecombe')
    expect(sgRow).toHaveTextContent('SG')
    expect(utilRow).toHaveTextContent('UTIL')
    expect(screen.getByText('No bench players yet.')).toBeInTheDocument()
  })

  it('shows dynasty players in every eligible position tab based on overall list', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({ data: [] })
    draftApi.getBestAvailable.mockResolvedValue({ data: { overall: [], byPosition: {} } })
    rankingsApi.getRemainingForDraft.mockResolvedValue({
      data: {
        overall: [
          {
            playerId: '1128',
            playerName: 'Bradley Beal',
            position: 'SG/SF',
            team: 'FA',
            rookie: false,
            blendedScore: 0.42,
            finalRank: 44,
          },
        ],
        byPosition: {
          SG: [
            {
              playerId: '1128',
              playerName: 'Bradley Beal',
              position: 'SG/SF',
              team: 'FA',
              rookie: false,
              blendedScore: 0.42,
              finalRank: 44,
            },
          ],
          SF: [],
        },
        rookies: [],
      },
    })
    playersApi.getAll.mockResolvedValue({ data: [] })
    leaguesApi.getLeague.mockResolvedValue({
      data: { roster_positions: ['PG', 'SG', 'SF', 'PF', 'C', 'BN'] },
    })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    await screen.findByText('@hoopsdynasty')

    await userEvent.click(screen.getByRole('button', { name: 'Dynasty + Rookies' }))
    await userEvent.click(screen.getByRole('button', { name: /\bSF\b/ }))

    expect(await screen.findByText('Bradley Beal')).toBeInTheDocument()
  })

  it('shows multi-position Sleeper players in the SF tab from the positional bucket', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({ data: [] })
    draftApi.getBestAvailable.mockResolvedValue({
      data: {
        overall: [
          { playerId: '1128', fullName: 'Bradley Beal', position: 'SG', team: 'FA' },
        ],
        byPosition: {
          SG: [
            { playerId: '1128', fullName: 'Bradley Beal', position: 'SG/SF', team: 'FA' },
          ],
          SF: [
            { playerId: '1128', fullName: 'Bradley Beal', position: 'SG/SF', team: 'FA' },
          ],
        },
      },
    })
    rankingsApi.getRemainingForDraft.mockResolvedValue({ data: { overall: [], byPosition: {}, rookies: [] } })
    playersApi.getAll.mockResolvedValue({ data: [] })
    leaguesApi.getLeague.mockResolvedValue({ data: {} })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    await screen.findByText('@hoopsdynasty')
    await userEvent.click(screen.getByRole('button', { name: /\bSF\b/ }))

    expect(await screen.findByText('Bradley Beal')).toBeInTheDocument()
  })

  it('shows counts on draft helper position tabs', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({ data: [] })
    draftApi.getBestAvailable.mockResolvedValue({
      data: {
        overall: [
          { playerId: '1', fullName: 'Player One', position: 'PG/SG', eligiblePositions: 'PG/SG', team: 'A' },
          { playerId: '2', fullName: 'Player Two', position: 'SF/PF', eligiblePositions: 'SF/PF', team: 'B' },
        ],
        byPosition: {},
      },
    })
    rankingsApi.getRemainingForDraft.mockResolvedValue({
      data: {
        overall: [
          { playerId: '1', playerName: 'Player One', position: 'PG/SG', eligiblePositions: 'PG/SG', rookie: false },
          { playerId: '2', playerName: 'Player Two', position: 'SF/PF', eligiblePositions: 'SF/PF', rookie: true },
        ],
        byPosition: {},
        rookies: [
          { playerId: '2', playerName: 'Player Two', position: 'SF/PF', eligiblePositions: 'SF/PF', rookie: true },
        ],
      },
    })
    playersApi.getAll.mockResolvedValue({ data: [] })
    leaguesApi.getLeague.mockResolvedValue({ data: {} })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    await screen.findByText('@hoopsdynasty')
    expect(screen.getByRole('button', { name: 'ALL (2)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'PG (1)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'SF (1)' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Dynasty + Rookies' }))

    expect(screen.getByRole('button', { name: 'ROOKIES (1)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'PF (1)' })).toBeInTheDocument()
  })

  it('falls back to players API when best available response is empty', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({
      data: { status: 'drafting', settings: {}, league_id: 'L1' },
    })
    draftApi.getPicks.mockResolvedValue({ data: [] })
    draftApi.getMyPicks.mockResolvedValue({ data: [] })
    draftApi.getBestAvailable.mockResolvedValue({ data: { overall: [], byPosition: {} } })
    rankingsApi.getRemainingForDraft.mockResolvedValue({ data: { overall: [], byPosition: {}, rookies: [] } })
    playersApi.getAll.mockResolvedValue({
      data: [
        {
          playerId: '1',
          fullName: 'Fallback Guard',
          position: 'PG/SG',
          team: 'DAL',
          searchRank: 20,
          fantasyPtsAvg: 25,
          gamesPlayed: 50,
          avgPts: 18,
          avgReb: 4,
          avgAst: 6,
          avgStl: 1,
          avgBlk: 0,
        },
      ],
    })
    leaguesApi.getLeague.mockResolvedValue({ data: {} })

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    expect(await screen.findByText('Fallback Guard')).toBeInTheDocument()
    expect(playersApi.getAll).toHaveBeenCalled()
  })

  it('returns to setup screen when changing draft', async () => {
    draftApi.resolveUser.mockResolvedValue({
      data: { userId: 'U1', username: 'hoopsdynasty' },
    })
    draftApi.getDraft.mockResolvedValue({ data: { status: 'drafting', settings: {} } })
    stubBoardApis()

    render(<DraftHelper />)

    await userEvent.type(screen.getByLabelText('Sleeper Username'), 'hoopsdynasty')
    await userEvent.type(screen.getByLabelText('League Draft ID'), '123')
    await userEvent.click(screen.getByRole('button', { name: 'Connect to Draft' }))

    expect(await screen.findByText('@hoopsdynasty')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Change Draft' }))

    expect(screen.getByRole('button', { name: 'Connect to Draft' })).toBeInTheDocument()
    expect(screen.getByLabelText('Sleeper Username')).toBeInTheDocument()
  })
})
