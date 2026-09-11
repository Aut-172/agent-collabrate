import React from 'react'
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest'
import {cleanup,render,screen,waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from './main.jsx'

const okResponse=body=>Promise.resolve({ok:true,status:200,json:()=>Promise.resolve(body)})

describe('project-scoped navigation',()=>{
  beforeEach(()=>{
    localStorage.clear()
    localStorage.setItem('ac_token','test-token')
  })

  afterEach(()=>{
    cleanup()
    vi.unstubAllGlobals()
  })

  it('keeps empty member and audit pages renderable during repeated navigation',async()=>{
    const fetchMock=vi.fn(path=>{
      if(path==='/api/projects')return okResponse([])
      if(path==='/api/workflows')return okResponse([])
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'项目概览'})

    for(let index=0;index<20;index++){
      await user.click(screen.getByRole('button',{name:'项目成员'}))
      expect(screen.getByText('暂无项目，请先创建项目')).toBeTruthy()
      await user.click(screen.getByRole('button',{name:'审计日志'}))
      expect(screen.getByText('暂无项目，创建项目后可查看审计日志')).toBeTruthy()
    }

    expect(screen.queryByText('页面暂时无法显示')).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('aborts stale member requests when switching to audit logs',async()=>{
    let abortedMemberRequests=0
    const auditPage={content:[{id:1,action:'PROJECT_CREATED',entityType:'PROJECT',entityId:7,actorUserId:1,createdAt:'2026-09-11T08:00:00Z'}]}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects')return okResponse([{id:7,name:'测试项目',ciStatus:'CI_NOT_CONFIGURED'}])
      if(path==='/api/workflows')return okResponse([])
      if(path.startsWith('/api/audit-logs'))return okResponse(auditPage)
      if(path==='/api/projects/7/members')return new Promise((resolve,reject)=>{
        options.signal?.addEventListener('abort',()=>{
          abortedMemberRequests++
          reject(new DOMException('Aborted','AbortError'))
        },{once:true})
      })
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})

    for(let index=0;index<10;index++){
      await user.click(screen.getByRole('button',{name:'项目成员'}))
      expect(screen.getByText('正在加载项目成员')).toBeTruthy()
      await user.click(screen.getByRole('button',{name:'审计日志'}))
      await screen.findByText(/PROJECT_CREATED/)
    }

    await waitFor(()=>expect(abortedMemberRequests).toBe(10))
    expect(screen.queryByText('页面暂时无法显示')).toBeNull()
  })
})
