import React from 'react'
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest'
import {cleanup,render,screen,waitFor,within} from '@testing-library/react'
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
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
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
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('aborts stale member requests when switching to audit logs',async()=>{
    let abortedMemberRequests=0
    const auditPage={content:[{id:1,action:'PROJECT_CREATED',entityType:'PROJECT',entityId:7,actorUserId:1,createdAt:'2026-09-11T08:00:00Z'}]}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects')return okResponse([{id:7,name:'测试项目',ciStatus:'CI_NOT_CONFIGURED'}])
      if(path==='/api/workflows')return okResponse([])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
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

  it('creates and selects a project from the top bar',async()=>{
    const created={id:9,name:'订单服务',repositoryUrl:'https://github.com/example/orders',gitProvider:'github',defaultBranch:'main',ciStatus:'CI_NOT_CONFIGURED'}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects'&&options.method==='POST')return okResponse(created)
      if(path==='/api/projects')return okResponse([])
      if(path==='/api/workflows')return okResponse([])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'项目概览'})
    const projectSelector=screen.getByRole('combobox',{name:'当前项目'})
    const openButton=screen.getByRole('button',{name:'创建项目'})
    expect(projectSelector.nextElementSibling).toBe(openButton)
    await user.click(openButton)
    const dialog=screen.getByRole('dialog',{name:'创建项目'})
    await user.type(within(dialog).getByRole('textbox',{name:'项目名称'}),'订单服务')
    await user.type(within(dialog).getByRole('textbox',{name:'Git 仓库地址'}),'https://github.com/example/orders')
    await user.click(within(dialog).getByRole('button',{name:'创建项目'}))

    await screen.findByRole('heading',{name:'订单服务'})
    expect(screen.queryByRole('dialog',{name:'创建项目'})).toBeNull()
    expect(screen.getByRole('combobox',{name:'当前项目'}).value).toBe('9')
    const createCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/projects'&&options.method==='POST')
    expect(JSON.parse(createCall[1].body)).toEqual({name:'订单服务',repositoryUrl:'https://github.com/example/orders',defaultBranch:'main',gitProvider:'github'})
  })

  it('creates a root workflow with a read-only empty parent Intent',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_REQUIRED',status:'ACTIVE',createdBy:1}
    const created={id:21,projectId:7,title:'服务边界设计',description:'明确服务边界和验收约束',intentLevel:'ARCHITECTURE',completionMode:'ARCHITECTURE_BASELINE',parentWorkflowId:null,status:'INTENT',nextAction:'GENERATE_DESIGN'}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects/7/workflows'&&options.method==='POST')return okResponse(created)
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER'}])
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([{id:99,projectId:8,title:'其他项目工作流',status:'INTENT'}])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    expect(screen.queryByText('其他项目工作流')).toBeNull()
    await user.click(screen.getByRole('button',{name:'创建工作流'}))
    const dialog=screen.getByRole('dialog',{name:'创建工作流'})
    const parent=within(dialog).getByRole('textbox',{name:'父级 Intent'})
    expect(within(dialog).getByRole('textbox',{name:'目标与验收描述'}).placeholder).toContain('例如：支持按时间范围导出订单 CSV')
    expect(parent.value).toBe('无')
    expect(parent.readOnly).toBe(true)
    await user.click(within(dialog).getByRole('radio',{name:/^Architecture/}))
    await user.type(within(dialog).getByRole('textbox',{name:'工作流标题'}),'服务边界设计')
    await user.type(within(dialog).getByRole('textbox',{name:'目标与验收描述'}),'明确服务边界和验收约束')
    await user.click(within(dialog).getByRole('button',{name:'创建工作流'}))

    await screen.findByText('服务边界设计')
    expect(screen.queryByRole('dialog',{name:'创建工作流'})).toBeNull()
    const createCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/projects/7/workflows'&&options.method==='POST')
    expect(JSON.parse(createCall[1].body)).toEqual({title:'服务边界设计',description:'明确服务边界和验收约束',intentLevel:'ARCHITECTURE',parentWorkflowId:null})
  })

  it('lets a leader initialize the project scaffold and CI without choosing Intent level',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_NOT_CONFIGURED',status:'ACTIVE',createdBy:1}
    const created={id:22,projectId:7,title:'初始化 Actions',description:'建立项目 CI 门禁',intentLevel:'FEATURE',completionMode:'CI_BOOTSTRAP',parentWorkflowId:null,status:'INTENT'}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects/7/ci-bootstrap'&&options.method==='POST')return okResponse(created)
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER'}])
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByRole('button',{name:'创建工作流'}))
    const dialog=screen.getByRole('dialog',{name:'创建工作流'})
    const bootstrap=within(dialog).getByRole('radio',{name:/^初始化工程与 CI/})
    await waitFor(()=>expect(bootstrap.disabled).toBe(false))
    await user.click(bootstrap)
    expect(within(dialog).queryByRole('group',{name:'Intent 级别'})).toBeNull()
    expect(within(dialog).getByText(/仅用于建立初始工程与首条 CI/)).toBeTruthy()
    expect(within(dialog).getByRole('textbox',{name:'目标与验收描述'}).placeholder).toContain('例如：建立 Spring Boot 基础工程')
    await user.type(within(dialog).getByRole('textbox',{name:'工作流标题'}),'初始化 Actions')
    await user.type(within(dialog).getByRole('textbox',{name:'目标与验收描述'}),'建立项目 CI 门禁')
    await user.click(within(dialog).getByRole('button',{name:'初始化工程与 CI'}))

    await screen.findByText('初始化 Actions')
    const createCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/projects/7/ci-bootstrap'&&options.method==='POST')
    expect(JSON.parse(createCall[1].body)).toEqual({title:'初始化 Actions',description:'建立项目 CI 门禁'})
  })

  it('shows CI initialization to members without allowing selection',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_NOT_CONFIGURED',status:'ACTIVE',createdBy:1}
    const fetchMock=vi.fn((path)=>{
      if(path==='/api/projects/7/members')return okResponse([{userId:2,username:'member',projectRole:'MEMBER'}])
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([])
      if(path==='/api/me')return okResponse({userId:2,username:'member'})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByRole('button',{name:'创建工作流'}))
    const dialog=screen.getByRole('dialog',{name:'创建工作流'})
    const bootstrap=within(dialog).getByRole('radio',{name:/^初始化工程与 CI/})
    await waitFor(()=>expect(within(dialog).getByText('只有项目 Leader 可以初始化工程与 CI。')).toBeTruthy())
    expect(bootstrap.disabled).toBe(true)
    expect(within(dialog).getByRole('radio',{name:/^普通工作流/}).checked).toBe(true)
    expect(within(dialog).getByRole('group',{name:'Intent 级别'})).toBeTruthy()
  })

  it('lets a project leader invite a registered user by username',async()=>{
    const leader={id:11,userId:1,username:'leader',projectRole:'LEADER',profileCompleted:false,profileVersion:0}
    const invited={id:12,userId:2,username:'member',projectRole:'MEMBER',profileCompleted:false,profileVersion:0}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects/7/members'&&options.method==='POST')return okResponse(invited)
      if(path==='/api/projects/7/members')return okResponse([leader])
      if(path==='/api/projects')return okResponse([{id:7,name:'测试项目',ciStatus:'CI_NOT_CONFIGURED'}])
      if(path==='/api/workflows')return okResponse([])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'项目成员'}))
    await screen.findByText(/^leader/,{selector:'.member-row strong'})
    await user.click(screen.getByRole('button',{name:'邀请成员'}))
    const dialog=screen.getByRole('dialog',{name:'邀请成员'})
    await user.type(within(dialog).getByRole('textbox',{name:'成员用户名'}),'member')
    await user.click(within(dialog).getByRole('button',{name:'邀请成员'}))

    const invitedRow=(await screen.findByText('member')).closest('.list-row')
    expect(within(invitedRow).getByText(/Member/)).toBeTruthy()
    const inviteCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/projects/7/members'&&options.method==='POST')
    expect(JSON.parse(inviteCall[1].body)).toEqual({username:'member'})
  })

  it('lets members view profiles and edit only their own profile',async()=>{
    const leader={id:11,userId:1,username:'leader',projectRole:'LEADER',profileCompleted:true,profileVersion:2,weeklyCapacityPoints:30,availability:'FULL_TIME',capabilityProfile:{summary:'后端负责人',responsibilities:['API 设计'],skills:['Java'],experience:['REST'],preferredTaskTypes:['backend'],limitations:[],availability:'FULL_TIME',weeklyCapacityPoints:30,notes:'负责核心服务'}}
    const member={id:12,userId:2,username:'member',projectRole:'MEMBER',profileCompleted:false,profileVersion:0}
    const saved={...member,profileCompleted:true,profileVersion:1,weeklyCapacityPoints:32,availability:'FULL_TIME',capabilityProfile:{summary:'技能：React',responsibilities:[],skills:['React'],experience:[],preferredTaskTypes:[],limitations:[],availability:'FULL_TIME',weeklyCapacityPoints:32,notes:''}}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects/7/members/me/profile'&&options.method==='PUT')return okResponse(saved)
      if(path==='/api/projects/7/members')return okResponse([leader,member])
      if(path==='/api/projects')return okResponse([{id:7,name:'测试项目',ciStatus:'CI_NOT_CONFIGURED'}])
      if(path==='/api/workflows')return okResponse([])
      if(path==='/api/me')return okResponse({userId:2,username:'member'})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'项目成员'}))
    const leaderRow=(await screen.findByText(/^leader/,{selector:'.member-row strong'})).closest('.list-row')
    const memberRow=screen.getByText(/^member/,{selector:'.member-row strong'}).closest('.list-row')
    expect(screen.queryByRole('button',{name:'邀请成员'})).toBeNull()
    expect(within(leaderRow).queryByRole('button',{name:'编辑能力画像'})).toBeNull()
    expect(within(memberRow).getByRole('button',{name:'编辑能力画像'})).toBeTruthy()

    await user.click(within(leaderRow).getByRole('button',{name:'查看能力画像'}))
    expect(screen.getByRole('dialog',{name:'leader 的能力画像'})).toBeTruthy()
    expect(screen.getByText('Java')).toBeTruthy()
    expect(screen.getByText('高')).toBeTruthy()
    expect(screen.queryByText('概要')).toBeNull()
    expect(screen.queryByText('每周容量')).toBeNull()
    await user.click(screen.getByRole('button',{name:'关闭能力画像'}))

    await user.click(within(memberRow).getByRole('button',{name:'编辑能力画像'}))
    const editor=screen.getByRole('dialog',{name:'编辑能力画像'})
    await user.type(within(editor).getByRole('textbox',{name:'技能'}),'React')
    expect(within(editor).getByRole('textbox',{name:'技能'}).placeholder).toContain('Java')
    expect(within(editor).getByRole('textbox',{name:'经验'}).placeholder).toContain('3 年后端开发')
    expect(within(editor).getByRole('textbox',{name:'偏好任务'}).placeholder).toContain('后端功能开发')
    expect(within(editor).getByRole('textbox',{name:'限制'}).placeholder).toContain('暂不承担移动端开发')
    expect(within(editor).queryByRole('textbox',{name:'概要'})).toBeNull()
    expect(within(editor).queryByRole('spinbutton',{name:'每周容量'})).toBeNull()
    expect(within(editor).getByText(/能力画像会影响任务分配/)).toBeTruthy()
    await user.selectOptions(within(editor).getByRole('combobox',{name:'投入程度'}),'HIGH')
    await user.click(within(editor).getByRole('button',{name:'保存画像'}))

    await waitFor(()=>expect(screen.queryByRole('dialog',{name:'编辑能力画像'})).toBeNull())
    expect(screen.getByText(/Member · 画像 v1/)).toBeTruthy()
    const updateCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/projects/7/members/me/profile'&&options.method==='PUT')
    expect(JSON.parse(updateCall[1].body)).toMatchObject({summary:'技能：React',responsibilities:[],skills:['React'],availability:'FULL_TIME',weeklyCapacityPoints:32,notes:''})
  })
})
