import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { EndpointsPage } from './EndpointsPage'

const endpoint = { id: 'endpoint-1', name: 'Primary endpoint', url: 'https://example.com/hook', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
function renderPage(fetchMock: ReturnType<typeof vi.fn>) { vi.stubGlobal('fetch', fetchMock); render(<MemoryRouter initialEntries={['/app/app-a/endpoints']}><Routes><Route path="/app/:applicationId/endpoints" element={<EndpointsPage />} /></Routes></MemoryRouter>) }
afterEach(() => vi.unstubAllGlobals())

it('creates one endpoint for same-tick submits and clears the one-time secret', async () => {
 const fetchMock=vi.fn((url:string,init?:RequestInit)=>{if(url.includes('/auth/csrf'))return Promise.resolve(json({token:'csrf'}));if(url.endsWith('/api/v1/applications/app-a/endpoints')&&init?.method==='POST')return Promise.resolve(json({...endpoint,id:'endpoint-2',name:'New endpoint',signingSecret:'test-secret-phase2-signing'},201));return Promise.resolve(json([endpoint]))})
 renderPage(fetchMock);await screen.findByText('Primary endpoint');await userEvent.click(screen.getByRole('button',{name:'Add endpoint'}));await userEvent.type(screen.getByLabelText('Name'),'New endpoint');await userEvent.type(screen.getByLabelText('Destination URL'),'https://example.com/new');const form=screen.getByLabelText('Destination URL').closest('form')!;fireEvent.submit(form);fireEvent.submit(form);expect(await screen.findByText('test-secret-phase2-signing')).toBeInTheDocument();expect(fetchMock.mock.calls.filter(([url,init])=>String(url).endsWith('/api/v1/applications/app-a/endpoints')&&(init as RequestInit).method==='POST')).toHaveLength(1);await userEvent.keyboard('{Escape}');expect(screen.queryByText('test-secret-phase2-signing')).not.toBeInTheDocument()
})

it('renders endpoint error retry and empty states without fake health data', async()=>{let calls=0;const fetchMock=vi.fn(()=>Promise.resolve(++calls===1?json({message:'Unavailable'},500):json([])));renderPage(fetchMock);expect(await screen.findByRole('alert')).toBeInTheDocument();await userEvent.click(screen.getByRole('button',{name:'Try again'}));expect(await screen.findByText('No endpoints yet')).toBeInTheDocument();expect(screen.queryByText(/health|latency|success rate/i)).not.toBeInTheDocument()})
