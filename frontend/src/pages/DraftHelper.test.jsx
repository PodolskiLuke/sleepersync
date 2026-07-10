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
  playersApi: { getAll: vi.fn() },
  rankingsApi: { getRemainingForDraft: vi.fn() },
}))

import { draftApi, playersApi, rankingsApi } from '../api/axiosClient'
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
    // The session is persisted for next time.
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY))
    expect(saved).toMatchObject({ userId: 'U1', username: 'hoopsdynasty', draftId: '123' })
  })
})
