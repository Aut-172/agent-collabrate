import React from 'react'
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest'
import {cleanup,fireEvent,render,screen,waitFor,within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App,BuildPlanSection,CallLogs,ChildIntentTable,DocumentDecisionPanel,EvidencePanel,Overview,appendResolvedDecisions,formatElapsed,sortWorkflows,stateLabel} from './main.jsx'

const okResponse=body=>Promise.resolve({ok:true,status:200,json:()=>Promise.resolve(body)})

describe('status labels',()=>{
  it('uses the Intent and Task labels consistently',()=>{
    expect(stateLabel('INTENT')).toBe('Intent Proposed')
    expect(stateLabel('TASKS_READY')).toBe('Tasks Ready')
  })
})

describe('document decisions',()=>{
  it('appends resolved decisions to downloaded document content',()=>{
    const document={id:31,content:'# Design'}
    const decisions=[{documentVersionId:31,status:'RESOLVED',decisionKey:'DEC-001',question:'是否允许嵌套？',selectedOption:'OPT-B',options:[{key:'OPT-A',label:'一层'},{key:'OPT-B',label:'无限'}]}]
    const output=appendResolvedDecisions(document.content,document,decisions)
    expect(output).toContain('## 已确认决策')
    expect(output).toContain('最终选择：无限 (`OPT-B`)')
  })

  it('formats generation durations for the AI status line',()=>{
    expect(formatElapsed(0)).toBe('0min00s')
    expect(formatElapsed(125)).toBe('2min05s')
  })

  it('shows the recommendation and submits the member selection',async()=>{
    const onResolve=vi.fn()
    const item={id:41,decisionKey:'DEC-001',question:'回复是否允许嵌套？',recommendedOption:'OPT-A',unresolvedImpact:'无法确定数据关系',status:'OPEN',options:[{key:'OPT-A',label:'只允许一层回复'},{key:'OPT-B',label:'允许无限嵌套'}]}
    const user=userEvent.setup()

    render(<DocumentDecisionPanel items={[item]} busy={false} canResolve onResolve={onResolve}/>)

    expect(screen.getByText('Open')).toBeTruthy()
    const recommendedOption=screen.getByRole('radio',{name:/只允许一层回复/}).closest('label')
    expect(within(recommendedOption).getByText('OPT-A')).toBeTruthy()
    expect(within(recommendedOption).getByText(/推荐/)).toBeTruthy()
    await user.click(screen.getByRole('radio',{name:/允许无限嵌套/}))
    await user.click(screen.getByRole('button',{name:'确认选择 DEC-001'}))
    expect(onResolve).toHaveBeenCalledWith(41,'OPT-B')
  })
})

describe('build plan granularity actions',()=>{
  afterEach(()=>cleanup())
  const plan={intentLevel:'FEATURE',tasks:[{taskKey:'TASK-A',title:'A'},{taskKey:'TASK-B',title:'B'}],assignments:[]}
  const warnings=[{message:'拆分过细',taskKeys:['TASK-A','TASK-B']}]

  it('sends merge selections and the audited keep-split reason',async()=>{
    const onGranularity=vi.fn()
    const user=userEvent.setup()
    render(<BuildPlanSection workflow={{status:'BUILD_PLAN_PROPOSED'}} document={{versionNo:1,confirmed:false}} plan={plan} warnings={warnings} editing={false} draft='' busy={false} canApprovePlan={false} onEdit={vi.fn()} onDraft={vi.fn()} onCancel={vi.fn()} onSave={vi.fn()} onApprove={vi.fn()} onGranularity={onGranularity} onCreateTasks={vi.fn()}/> )
    expect(screen.getByText(/拆分过细/)).toBeTruthy()
    const checkboxes=screen.getAllByRole('checkbox'); await user.click(checkboxes[0]); await user.click(checkboxes[1])
    await user.type(screen.getByRole('textbox',{name:'拆分覆盖理由'}),'两个任务必须独立验收')
    await user.click(screen.getByRole('button',{name:'合并选中 Task'}))
    expect(onGranularity).toHaveBeenCalledWith({operation:'MERGE',taskKeys:['TASK-A','TASK-B'],reason:'两个任务必须独立验收'})
    await user.click(screen.getByRole('button',{name:'保留当前拆分'}))
    expect(onGranularity).toHaveBeenCalledWith({operation:'KEEP_SPLIT',taskKeys:['TASK-A','TASK-B'],reason:'两个任务必须独立验收'})
  })

  it('requires a reason to approve warnings and clears stale input for a new plan version',async()=>{
    const onApprove=vi.fn()
    const user=userEvent.setup()
    const props={workflow:{status:'BUILD_PLAN_PROPOSED'},plan,warnings,editing:false,draft:'',busy:false,canApprovePlan:true,onEdit:vi.fn(),onDraft:vi.fn(),onCancel:vi.fn(),onSave:vi.fn(),onApprove,onGranularity:vi.fn(),onCreateTasks:vi.fn()}
    const view=render(<BuildPlanSection {...props} document={{versionNo:1,confirmed:false}}/> )
    const approve=screen.getByRole('button',{name:'批准 v1'})
    expect(approve.disabled).toBe(true)
    await user.type(screen.getByRole('textbox',{name:'拆分覆盖理由'}),'独立发布窗口')
    expect(approve.disabled).toBe(false)
    await user.click(approve)
    expect(onApprove).toHaveBeenCalledWith('独立发布窗口')

    view.rerender(<BuildPlanSection {...props} document={{versionNo:2,confirmed:false}}/> )
    await waitFor(()=>expect(screen.getByRole('textbox',{name:'拆分覆盖理由'}).value).toBe(''))
    expect(screen.getByRole('button',{name:'批准 v2'}).disabled).toBe(true)
  })

  it('offers plan regeneration when an assignee snapshot is stale',async()=>{
    const onRegeneratePlan=vi.fn()
    const user=userEvent.setup()
    render(<BuildPlanSection workflow={{status:'BUILD_PLAN_PROPOSED'}} document={{versionNo:1,confirmed:false}} plan={{intentLevel:'FEATURE',tasks:[{taskKey:'TASK-A',title:'A'}],assignments:[]} } warnings={[]} editing={false} draft='' busy={false} canApprovePlan planNeedsRegeneration onRegeneratePlan={onRegeneratePlan} onEdit={vi.fn()} onDraft={vi.fn()} onCancel={vi.fn()} onSave={vi.fn()} onApprove={vi.fn()} onGranularity={vi.fn()} onCreateTasks={vi.fn()}/>)
    await user.click(screen.getByRole('button',{name:'重新生成计划'}))
    expect(onRegeneratePlan).toHaveBeenCalledOnce()
  })

  it('edits task assignments with member selectors and saves a new plan',async()=>{
    const onSave=vi.fn(async()=>true)
    const user=userEvent.setup()
    const plan={intentLevel:'FEATURE',staffingRecommendation:{mode:'SINGLE_OWNER',recommendedTeamSize:1,reason:'',confidence:1},tasks:[{taskKey:'TASK-A',title:'A'}],assignments:[{taskKey:'TASK-A',userId:1,projectRole:'LEADER',profileVersion:1,workloadSnapshot:{openEffortPoints:0,weeklyCapacityPoints:36},fitReason:'原分配',assignmentScore:1}],alternatives:[],warnings:[]}
    const members=[{userId:1,username:'leader',projectRole:'LEADER',profileVersion:1,profileCompleted:true,weeklyCapacityPoints:36},{userId:2,username:'member',projectRole:'MEMBER',profileVersion:2,profileCompleted:true,weeklyCapacityPoints:24}]
    render(<BuildPlanSection workflow={{status:'BUILD_PLAN_PROPOSED'}} document={{versionNo:1,confirmed:false}} plan={plan} members={members} warnings={[]} editing={false} draft='' busy={false} canApprovePlan onEdit={vi.fn()} onDraft={vi.fn()} onCancel={vi.fn()} onSave={onSave} onApprove={vi.fn()} onGranularity={vi.fn()} onCreateTasks={vi.fn()}/>)
    await user.click(screen.getByRole('button',{name:'编辑任务分配'}))
    await user.selectOptions(screen.getByRole('combobox',{name:'任务 TASK-A 负责人'}),'2')
    await user.click(screen.getByRole('button',{name:'保存任务分配'}))
    expect(onSave).toHaveBeenCalledOnce()
    expect(JSON.parse(onSave.mock.calls[0][0]).assignments[0].userId).toBe(2)
  })

  it('sorts workflows by creation time and state-machine completion',()=>{
    const workflows=[
      {id:1,title:'早期',status:'INTENT',createdAt:'2026-09-10T00:00:00Z'},
      {id:2,title:'后期',status:'DONE',createdAt:'2026-09-12T00:00:00Z'},
      {id:3,title:'中期',status:'BUILD_PLAN_PROPOSED',createdAt:'2026-09-11T00:00:00Z'}
    ]
    expect(sortWorkflows(workflows,'created-desc').map(item=>item.id)).toEqual([2,3,1])
    expect(sortWorkflows(workflows,'created-asc').map(item=>item.id)).toEqual([1,3,2])
    expect(sortWorkflows(workflows,'status-desc').map(item=>item.id)).toEqual([2,3,1])
    expect(sortWorkflows(workflows,'status-asc').map(item=>item.id)).toEqual([1,3,2])
  })
})

describe('project-scoped navigation',()=>{
  beforeEach(()=>{
    localStorage.clear()
    localStorage.setItem('ac_token','test-token')
  })

  afterEach(()=>{
    cleanup()
    vi.restoreAllMocks()
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
    expect(fetchMock).toHaveBeenCalledTimes(4)
  },10000)

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

  it('shows Architecture child Intents as standalone Workflow rows',()=>{
    render(<ChildIntentTable items={[{title:'用户认证',description:'实现登录和权限校验',intentLevel:'FEATURE'},{title:'登录限流',description:'限制失败尝试次数',intentLevel:'CHANGE'}]}/>)
    expect(screen.getByRole('table')).toBeTruthy()
    expect(screen.getByText('用户认证')).toBeTruthy()
    expect(screen.getByText('CHANGE')).toBeTruthy()
    expect(screen.getAllByText('创建后作为独立 Workflow')).toHaveLength(2)
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
    expect(within(dialog).queryByRole('checkbox',{name:/要求 Pull Request/})).toBeNull()
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
    expect(within(dialog).getByRole('checkbox',{name:/要求 Pull Request/}).checked).toBe(true)
    expect(within(dialog).getByText(/仅用于建立初始工程与首条 CI/)).toBeTruthy()
    expect(within(dialog).getByRole('textbox',{name:'目标与验收描述'}).placeholder).toContain('例如：建立 Spring Boot 基础工程')
    await user.type(within(dialog).getByRole('textbox',{name:'工作流标题'}),'初始化 Actions')
    await user.type(within(dialog).getByRole('textbox',{name:'目标与验收描述'}),'建立项目 CI 门禁')
    await user.click(within(dialog).getByRole('button',{name:'初始化工程与 CI'}))

    await screen.findByText('初始化 Actions')
    const createCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/projects/7/ci-bootstrap'&&options.method==='POST')
    expect(JSON.parse(createCall[1].body)).toEqual({title:'初始化 Actions',description:'建立项目 CI 门禁',pullRequestRequired:true})
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

  it('allows editing an unconfirmed Design and keeps plan approval with the Leader',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_REQUIRED',status:'ACTIVE',createdBy:1}
    const workflow={id:21,projectId:7,title:'文档工作流',description:'验证文档编辑',intentLevel:'FEATURE',status:'BUILD_PLAN_PROPOSED',health:'HEALTHY',nextAction:'CONTINUE'}
    const documents=[
      {id:31,workflowId:21,documentType:'DESIGN',versionNo:1,source:'AGENT',contentFormat:'MARKDOWN',content:'# 原始 Design',confirmed:false},
      {id:32,workflowId:21,documentType:'SPEC',versionNo:1,source:'AGENT',contentFormat:'MARKDOWN',content:'# 原始 Spec',confirmed:false},
      {id:33,workflowId:21,documentType:'BUILD_PLAN',versionNo:1,source:'AGENT',contentFormat:'JSON',content:'{"intentLevel":"FEATURE"}',confirmed:false}
    ]
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([workflow])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      if(path==='/api/notifications')return okResponse([])
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER'}])
      if(path==='/api/workflows/21')return okResponse(workflow)
      if(path==='/api/workflows/21/documents')return okResponse(documents)
      if(path==='/api/workflows/21/tasks')return okResponse([])
      if(path==='/api/workflows/21/code-context')return okResponse({status:'CURRENT'})
      if(path==='/api/projects/7/repo-inventory/latest')return okResponse({status:'CURRENT'})
      if(path.startsWith('/api/projects/7/code-context/runs/latest'))return okResponse({id:91,status:'SUCCEEDED'})
      if(path.startsWith('/api/workflows/21/audit-logs'))return okResponse([])
      if(path==='/api/workflows/21/design'&&options.method==='PUT')return okResponse({...documents[0],versionNo:2,content:JSON.parse(options.body).content})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByText('文档工作流'))
    await screen.findByText('原始 Design')
    expect(screen.getByText('DESIGN').classList.contains('document-title-mark')).toBe(true)
    vi.stubGlobal('URL',{createObjectURL:vi.fn(()=>'blob:document'),revokeObjectURL:vi.fn()})
    const downloadButton=await screen.findByRole('button',{name:'下载 DESIGN 文档'})
    await user.click(downloadButton)
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(screen.getByRole('button',{name:'批准 v1'}).classList.contains('primary')).toBe(true)
    await user.click(screen.getAllByRole('button',{name:'编辑'})[0])
    const editor=screen.getByRole('textbox',{name:'编辑 DESIGN 文档'})
    await user.clear(editor)
    await user.type(editor,'# 修改后的 Design')
    await user.click(screen.getByRole('button',{name:'保存文档'}))
    await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith('/api/workflows/21/design',expect.objectContaining({method:'PUT'})))
    expect(screen.queryByRole('textbox',{name:'编辑 DESIGN 文档'})).toBeNull()
  })

  it('restores an active Design run when re-entering workflow details',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_REQUIRED',status:'ACTIVE',createdBy:1}
    const workflow={id:21,projectId:7,title:'生成中工作流',description:'恢复后台运行状态',intentLevel:'FEATURE',status:'INTENT',health:'HEALTHY',nextAction:'GENERATE_DESIGN',activeAgentRuns:[{id:99,workflowId:21,runType:'GENERATE_DESIGN',status:'RUNNING'}]}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([workflow])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      if(path==='/api/notifications')return okResponse([])
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER'}])
      if(path==='/api/workflows/21')return okResponse(workflow)
      if(path==='/api/workflows/21/documents')return okResponse([])
      if(path==='/api/workflows/21/tasks')return okResponse([])
      if(path==='/api/workflows/21/code-context')return okResponse({status:'CURRENT'})
      if(path==='/api/projects/7/repo-inventory/latest')return okResponse({status:'CURRENT'})
      if(path.startsWith('/api/projects/7/code-context/runs/latest'))return okResponse({id:91,status:'SUCCEEDED'})
      if(path.startsWith('/api/workflows/21/audit-logs'))return okResponse([])
      if(path==='/api/agent-runs/99')return okResponse(workflow.activeAgentRuns[0])
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByText('生成中工作流'))

    const generateButton=await screen.findByRole('button',{name:'生成 Design'})
    expect(generateButton.disabled).toBe(true)
    expect(screen.getByText('Running')).toBeTruthy()
    expect(screen.getByText('预计耗时：2-4min')).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledWith('/api/agent-runs/99',expect.any(Object))
  })

  it('shows waiting text instead of plan approval for a project member',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_REQUIRED',status:'ACTIVE',createdBy:1}
    const workflow={id:21,projectId:7,title:'成员工作流',description:'验证审批权限',intentLevel:'FEATURE',status:'BUILD_PLAN_PROPOSED',health:'HEALTHY',nextAction:'CONTINUE'}
    const fetchMock=vi.fn((path)=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([workflow])
      if(path==='/api/me')return okResponse({userId:2,username:'member'})
      if(path==='/api/notifications')return okResponse([])
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER'},{userId:2,username:'member',projectRole:'MEMBER'}])
      if(path==='/api/workflows/21')return okResponse(workflow)
      if(path==='/api/workflows/21/documents')return okResponse([{id:33,documentType:'BUILD_PLAN',versionNo:1,source:'AGENT',contentFormat:'JSON',content:'{"intentLevel":"FEATURE"}',confirmed:false}])
      if(path==='/api/workflows/21/tasks')return okResponse([])
      if(path==='/api/workflows/21/code-context')return okResponse({status:'CURRENT'})
      if(path==='/api/projects/7/repo-inventory/latest')return okResponse({status:'CURRENT'})
      if(path.startsWith('/api/projects/7/code-context/runs/latest'))return okResponse({id:91,status:'SUCCEEDED'})
      if(path.startsWith('/api/workflows/21/audit-logs'))return okResponse([])
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByText('成员工作流'))
    expect(await screen.findByText('等待 Leader 批准 Build Plan')).toBeTruthy()
    expect(screen.queryByRole('button',{name:'批准 v1'})).toBeNull()
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
    const saved={...member,profileCompleted:true,profileVersion:1,weeklyCapacityPoints:36,availability:'FULL_TIME',capabilityProfile:{summary:'技能：React',responsibilities:[],skills:['React'],experience:[],preferredTaskTypes:[],limitations:[],availability:'FULL_TIME',weeklyCapacityPoints:36,notes:''}}
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
    expect(JSON.parse(updateCall[1].body)).toMatchObject({summary:'技能：React',responsibilities:[],skills:['React'],availability:'FULL_TIME',weeklyCapacityPoints:36,notes:''})
  })

  it('opens a planned task and confirms its current package to start development',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_REQUIRED',status:'ACTIVE',createdBy:1}
    const workflow={id:21,projectId:7,title:'交付闭环',description:'验证任务交付流程',intentLevel:'FEATURE',status:'TASKS_READY',health:'HEALTHY',nextAction:'CONTINUE_TASKS'}
    let taskStatus='ASSIGNED'
    let deliveryAttempts=0
    const task=()=>({id:31,workflowId:21,externalKey:'TASK-001',title:'实现接口',description:'完成核心接口',effortPoints:3,status:taskStatus,sourcePlanVersion:1,sourceSpecVersion:1,branchName:'agent/task-001',currentPackageVersion:1,planDetails:{acceptanceCriteria:['接口测试通过'],verificationCommands:['npm test']},currentAssignment:{assigneeUserId:1,assignmentVersion:1,assignmentReason:'技能匹配',assignmentScore:0.9,profileVersion:1,workloadSnapshot:{openEffortPoints:3}}})
    const taskPackage={id:41,taskId:31,packageVersion:1,status:'CURRENT',contentMarkdown:'# TASK-001\n\n完成接口。',contentJson:{task:{taskId:'TASK-001'}},contentHash:`sha256:${'a'.repeat(64)}`,baseCommit:'abcdef1',codeContextVersionId:51,contextPlanId:61}
    const fetchMock=vi.fn((path,options={})=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([workflow])
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      if(path==='/api/workflows/21')return okResponse(workflow)
      if(path==='/api/workflows/21/documents')return okResponse([])
      if(path==='/api/workflows/21/tasks')return okResponse([task()])
      if(path==='/api/workflows/21/code-context')return okResponse({id:51,status:'CURRENT'})
      if(path==='/api/projects/7/repo-inventory/latest')return okResponse({id:71,status:'CURRENT'})
      if(path==='/api/tasks/31')return okResponse(task())
      if(path==='/api/tasks/31/packages/current')return okResponse(taskPackage)
      if(path==='/api/tasks/31/deliveries'||path==='/api/tasks/31/blockers'||path==='/api/tasks/31/git-operations'||path==='/api/tasks/31/ci-runs')return okResponse([])
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER',profileCompleted:true}])
      if(path==='/api/tasks/31/packages/1/confirm'&&options.method==='POST'){taskStatus='IN_PROGRESS';return okResponse({taskStatus})}
      if(path==='/api/tasks/31/delivery'&&options.method==='POST'){
        deliveryAttempts++
        if(deliveryAttempts===1)return Promise.resolve({ok:false,status:400,json:()=>Promise.resolve({code:'INVALID_FINAL_REPORT',message:'Final Report 不符合 JSON Schema'})})
        return okResponse({id:81,status:'SUBMITTED'})
      }
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    vi.stubGlobal('URL',{createObjectURL:vi.fn(()=>"blob:task-package"),revokeObjectURL:vi.fn()})
    vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{})
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByText('交付闭环'))
    await screen.findByText('TASK-001 · 实现接口')
    await user.click(screen.getByRole('button',{name:'查看任务'}))
    expect(await screen.findByRole('heading',{name:'任务包'})).toBeTruthy()
    expect(screen.getByRole('heading',{name:'TASK-001 · 实现接口'})).toBeTruthy()
    expect(screen.getByRole('note',{name:'分支开发要求'}).textContent).toContain('agent/task-001')
    expect(screen.getByRole('note',{name:'分支开发要求'}).textContent).toContain('默认分支 main 仅作为基线')
    expect(screen.getByRole('region',{name:'任务计划 JSON'}).querySelector('.json-key')?.textContent).toBe('"acceptanceCriteria"')
    expect(screen.getByRole('button',{name:'复制任务说明'}).title).toContain('可直接交给开发 Agent')
    expect(screen.getByRole('button',{name:'下载任务包 (.md)'}).title).toContain('内容相同')
    const moreMenu=screen.getByLabelText('更多任务包操作').closest('details')
    expect(moreMenu.open).toBe(false)
    await user.click(screen.getByLabelText('更多任务包操作'))
    expect(moreMenu.open).toBe(true)
    expect(screen.getByRole('button',{name:'下载结构化任务包 (.json)'}).title).toContain('自动化工具')
    await user.click(screen.getByRole('button',{name:'下载结构化任务包 (.json)'}))
    expect(await screen.findByText('结构化任务包 JSON 文件已开始下载。')).toBeTruthy()
    expect(moreMenu.open).toBe(false)
    await user.click(screen.getByRole('button',{name:'复制任务说明'}))
    expect(await screen.findByText('任务包已复制到剪贴板。')).toBeTruthy()
    await user.click(screen.getByRole('button',{name:'下载任务包 (.md)'}))
    expect(await screen.findByText('任务包 Markdown 文件已开始下载。')).toBeTruthy()
    await user.click(screen.getByRole('button',{name:'确认并开始开发'}))
    expect(await screen.findByRole('heading',{name:'提交交付'})).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledWith('/api/tasks/31/packages/1/confirm',expect.objectContaining({method:'POST'}))

    const jsonMode=screen.getByRole('button',{name:'JSON'})
    expect(jsonMode.classList.contains('active')).toBe(true)
    const jsonEditor=screen.getByRole('textbox',{name:'Final Report JSON'})
    expect(JSON.parse(jsonEditor.value)).toMatchObject({
      taskId:'TASK-001',packageId:41,packageVersion:1,packageHash:taskPackage.contentHash,
      codeContextVersionId:51,contextPlanId:61,baseCommitSha:'abcdef1',summary:'',
      tests:[{command:'npm test',status:'NOT_RUN',summary:''}],
      acceptanceCriteria:[{criterion:'接口测试通过',status:'NOT_VERIFIED',evidence:''}]
    })

    await user.click(screen.getByRole('button',{name:'表单'}))
    expect(screen.getByRole('textbox',{name:'交付摘要（顶层 summary）'})).toBeTruthy()
    expect(screen.getByRole('textbox',{name:'验证结果摘要（summary）'})).toBeTruthy()
    expect(screen.getByRole('textbox',{name:'验收证据（evidence）'})).toBeTruthy()
    const advanced=screen.getByText('更多交付信息').closest('details')
    expect(advanced.open).toBe(false)
    expect(screen.getByRole('option',{name:'需求需要澄清（REQUIREMENT_CLARIFICATION）'})).toBeTruthy()
    expect(screen.getByPlaceholderText(/用一句话概括阻塞及影响/)).toBeTruthy()
    expect(screen.getByPlaceholderText(/说明遇到的现象、已经尝试的方案及影响范围/)).toBeTruthy()
    expect(screen.getByPlaceholderText(/写出需要 Leader 明确回答的问题/)).toBeTruthy()
    expect(screen.getByRole('button',{name:'报告阻塞'}).classList.contains('primary')).toBe(true)

    await user.click(jsonMode)
    const refreshedJsonEditor=screen.getByRole('textbox',{name:'Final Report JSON'})
    const finalReport=JSON.parse(refreshedJsonEditor.value)
    finalReport.summary='完成核心接口并通过测试'
    finalReport.changedFiles=['src/main.jsx']
    finalReport.tests=[{command:'npm test',result:'执行完成但未说明状态',summary:'完成验证'}]
    finalReport.git.commitSha='abcdef1234567'
    fireEvent.change(refreshedJsonEditor,{target:{value:JSON.stringify(finalReport)}})
    await user.click(screen.getByRole('button',{name:'提交交付并开始 Git 校验'}))
    expect(await screen.findByText(/tests\[0\].*缺少必填字段 status/)).toBeTruthy()
    expect(screen.getByText(/已忽略 tests\[0\] 的未知字段：result/)).toBeTruthy()
    expect(deliveryAttempts).toBe(0)

    finalReport.tests=[{command:'npm test',status:'PASSED',summary:'全部测试通过'}]
    delete finalReport.blockers
    finalReport.agentComment='此字段不属于 Final Report Schema'
    fireEvent.change(refreshedJsonEditor,{target:{value:`\`\`\`json\n${JSON.stringify(finalReport)}\n\`\`\``}})
    await user.click(screen.getByRole('button',{name:'提交交付并开始 Git 校验'}))
    expect(await screen.findByText(/只粘贴一个纯 JSON 对象/)).toBeTruthy()
    expect(deliveryAttempts).toBe(0)

    fireEvent.change(refreshedJsonEditor,{target:{value:JSON.stringify(finalReport)}})
    await user.click(screen.getByRole('button',{name:'提交交付并开始 Git 校验'}))
    const deliveryPanel=screen.getByRole('heading',{name:'提交交付'}).closest('section')
    expect(within(deliveryPanel).getByText(/已补齐 blockers/)).toBeTruthy()
    expect(within(deliveryPanel).getByText(/已忽略顶层未知字段：agentComment/)).toBeTruthy()
    expect(await within(deliveryPanel).findByText('Final Report 不符合 JSON Schema [INVALID_FINAL_REPORT]')).toBeTruthy()
    const normalizedCall=fetchMock.mock.calls.find(([path,options])=>path==='/api/tasks/31/delivery'&&options.method==='POST')
    const normalizedReport=JSON.parse(normalizedCall[1].body).finalReport
    expect(normalizedReport.tests[0]).toEqual({command:'npm test',status:'PASSED',summary:'全部测试通过'})
    expect(normalizedReport.blockers).toEqual([])
    expect(normalizedReport).not.toHaveProperty('agentComment')

    await user.click(screen.getByRole('button',{name:'提交交付并开始 Git 校验'}))
    await waitFor(()=>expect(deliveryAttempts).toBe(2))
    const deliveryCall=fetchMock.mock.calls.filter(([path,options])=>path==='/api/tasks/31/delivery'&&options.method==='POST').at(-1)
    expect(JSON.parse(deliveryCall[1].body)).toMatchObject({
      packageId:41,packageVersion:1,packageHash:taskPackage.contentHash,
      branchName:'agent/task-001',commitSha:'abcdef1234567',pullRequestUrl:null,
      finalReport:{summary:'完成核心接口并通过测试',git:{branchName:'agent/task-001',commitSha:'abcdef1234567',pullRequestUrl:null}}
    })
  })

  it('shows an actionable Chinese message for an unknown CI run',async()=>{
    const retry=vi.fn()
    const user=userEvent.setup()
    render(<EvidencePanel deliveries={[]} gitOps={[]} ciRuns={[{id:71,commitSha:'abcdef1234567',status:'UNKNOWN',conclusion:'NO_CHECK_RUNS',lastSyncedAt:'2026-09-12T06:00:00Z'}]} canRetryCi busy={false} onRetryCi={retry}/>)

    expect(screen.getByText(/当前 Commit 尚无 CI 检查/)).toBeTruthy()
    expect(screen.getByText(/\.github\/workflows/)).toBeTruthy()
    expect(screen.getByText('Unknown')).toBeTruthy()
    await user.click(screen.getByRole('button',{name:'重新同步 CI'}))
    expect(retry).toHaveBeenCalledWith(71)
  })

  it('hides task package copy and download actions from non-assignees',async()=>{
    const project={id:7,name:'测试项目',repositoryUrl:'https://github.com/example/repo',defaultBranch:'main',ciStatus:'CI_REQUIRED',status:'ACTIVE'}
    const workflow={id:21,projectId:7,title:'权限任务',description:'验证任务包权限',intentLevel:'FEATURE',status:'TASKS_READY',health:'HEALTHY',nextAction:'CONTINUE_TASKS'}
    const task={id:31,workflowId:21,externalKey:'TASK-001',title:'负责人任务',description:'其他成员只读',effortPoints:3,status:'ASSIGNED',sourcePlanVersion:1,sourceSpecVersion:1,branchName:'agent/task-001',currentPackageVersion:1,planDetails:{scope:['src'],acceptanceCriteria:['测试通过']},currentAssignment:{assigneeUserId:1,assignmentVersion:1,assignmentReason:'技能匹配',profileVersion:1}}
    const taskPackage={id:41,taskId:31,packageVersion:1,status:'CURRENT',contentMarkdown:'# TASK-001\n\n仅供查看。',contentJson:{task:{taskId:'TASK-001'}},contentHash:`sha256:${'a'.repeat(64)}`,baseCommit:'abcdef1'}
    const fetchMock=vi.fn(path=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse([workflow])
      if(path==='/api/me')return okResponse({userId:2,username:'member'})
      if(path==='/api/notifications')return okResponse([])
      if(path==='/api/workflows/21')return okResponse(workflow)
      if(path==='/api/workflows/21/documents')return okResponse([])
      if(path==='/api/workflows/21/tasks')return okResponse([task])
      if(path==='/api/workflows/21/code-context')return okResponse({id:51,status:'CURRENT'})
      if(path==='/api/projects/7/repo-inventory/latest')return okResponse({id:71,status:'CURRENT'})
      if(path==='/api/tasks/31')return okResponse(task)
      if(path==='/api/tasks/31/packages/current')return okResponse(taskPackage)
      if(path==='/api/tasks/31/deliveries'||path==='/api/tasks/31/blockers'||path==='/api/tasks/31/git-operations'||path==='/api/tasks/31/ci-runs')return okResponse([])
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'owner',projectRole:'MEMBER',profileCompleted:true},{userId:2,username:'member',projectRole:'MEMBER',profileCompleted:true}])
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'工作流'}))
    await user.click(screen.getByText('权限任务'))
    await screen.findByText('TASK-001 · 负责人任务')
    await user.click(screen.getByRole('button',{name:'查看任务'}))
    expect(await screen.findByRole('heading',{name:'任务包'})).toBeTruthy()
    expect(screen.getByText('仅供查看。')).toBeTruthy()
    expect(screen.queryByRole('button',{name:'复制任务说明'})).toBeNull()
    expect(screen.queryByRole('button',{name:'下载任务包 (.md)'})).toBeNull()
    expect(screen.queryByLabelText('更多任务包操作')).toBeNull()
  })

  it('opens a project board with a default workflow and allows switching workflows',async()=>{
    const project={id:7,name:'测试项目',ciStatus:'CI_REQUIRED'}
    const workflows=[{id:21,projectId:7,title:'工作流一',status:'TASKS_READY'},{id:22,projectId:7,title:'工作流二',status:'IN_PROGRESS'}]
    const fetchMock=vi.fn(path=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse(workflows)
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      if(path==='/api/workflows/21/board'||path==='/api/workflows/22/board')return okResponse({columns:[]})
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByRole('heading',{name:'测试项目'})
    await user.click(screen.getByRole('button',{name:'任务看板'}))
    const selector=await screen.findByRole('combobox',{name:'看板 Workflow'})
    await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith('/api/workflows/21/board',expect.any(Object)))
    expect(screen.queryByText('请先从工作流列表选择一个 Workflow')).toBeNull()
    await user.selectOptions(selector,'22')
    await waitFor(()=>expect(fetchMock).toHaveBeenCalledWith('/api/workflows/22/board',expect.any(Object)))
  })

  it('aggregates the current users project tasks and shows assignment notifications',async()=>{
    const project={id:7,name:'测试项目',ciStatus:'CI_REQUIRED'}
    const workflows=[{id:21,projectId:7,title:'订单工作流',status:'TASKS_READY'},{id:22,projectId:7,title:'库存工作流',status:'TASKS_READY'}]
    const ownTask={id:31,workflowId:21,externalKey:'TASK-001',title:'实现订单接口',description:'增加订单查询',effortPoints:3,status:'ASSIGNED',branchName:'agent/task-001',currentPackageVersion:1,currentAssignment:{assigneeUserId:1}}
    const otherTask={id:32,workflowId:22,externalKey:'TASK-002',title:'库存任务',status:'ASSIGNED',currentAssignment:{assigneeUserId:2}}
    const fetchMock=vi.fn(path=>{
      if(path==='/api/projects')return okResponse([project])
      if(path==='/api/workflows')return okResponse(workflows)
      if(path==='/api/me')return okResponse({userId:1,username:'leader'})
      if(path==='/api/notifications')return okResponse([{id:91,type:'TASK_ASSIGNED',entityType:'TASK',entityId:31,title:'你收到一个新任务',content:'TASK-001：实现订单接口',readAt:null,createdAt:'2026-09-12T01:00:00Z'}])
      if(path==='/api/workflows/21/tasks')return okResponse([ownTask])
      if(path==='/api/workflows/22/tasks')return okResponse([otherTask])
      if(path==='/api/tasks/31')return okResponse({...ownTask,sourcePlanVersion:1,planDetails:{},currentAssignment:{assigneeUserId:1,assignmentVersion:1,assignmentReason:'技能匹配',profileVersion:1}})
      if(path==='/api/tasks/31/packages/current')return okResponse(null)
      if(path==='/api/tasks/31/deliveries'||path==='/api/tasks/31/blockers'||path==='/api/tasks/31/git-operations'||path==='/api/tasks/31/ci-runs')return okResponse([])
      if(path==='/api/projects/7/members')return okResponse([{userId:1,username:'leader',projectRole:'LEADER',profileCompleted:true}])
      throw new Error(`Unexpected request: ${path}`)
    })
    vi.stubGlobal('fetch',fetchMock)
    const user=userEvent.setup()

    render(<App/>)
    await screen.findByLabelText('1 条未读通知')
    await user.click(screen.getByRole('button',{name:'我的任务'}))
    expect(await screen.findByRole('heading',{name:'TASK-001 · 实现订单接口'})).toBeTruthy()
    expect(screen.getByText('订单工作流')).toBeTruthy()
    expect(screen.queryByText('库存任务')).toBeNull()
    await user.click(screen.getByRole('button',{name:'查看任务详情'}))
    expect(await screen.findByRole('heading',{name:'任务信息'})).toBeTruthy()
    await user.click(screen.getByRole('button',{name:'我的任务'}))
    expect(await screen.findByRole('heading',{name:'我的任务'})).toBeTruthy()
  })
})

describe('project overview statistics',()=>{
  afterEach(()=>{
    cleanup()
    vi.unstubAllGlobals()
  })

  it('loads and displays workflow, task, and member statistics',async()=>{
    const stats={
      project:{activeWorkflowCount:1,openTaskCount:2},
      workflows:{total:3,byStatus:{IN_PROGRESS:2,DONE:1},dailyCreated:[{date:'2026-09-14',count:2}]},
      tasks:{byStatus:{ASSIGNED:1,BLOCKED:1},dailyCompleted:[{date:'2026-09-14',count:1}]},
      tokens:{callCount:2,callsWithUsage:2,inputTokens:100,outputTokens:50,reasoningTokens:10,totalTokens:160},
      members:[{userId:1,username:'leader',projectRole:'LEADER',availability:'FULL_TIME',openEffortPoints:8,weeklyCapacityPoints:16,openTaskCount:2,assignedTaskCount:2,completedTaskCount:1,workloadRatio:0.5}]
    }
    vi.stubGlobal('fetch',vi.fn(()=>okResponse(stats)))
    render(<Overview project={{id:7,name:'订单服务',repositoryUrl:'repo',ciStatus:'CI_REQUIRED'}} workflows={[{id:21,status:'IN_PROGRESS',title:'旧数据'}]}/>)

    expect(await screen.findByText('Workflow 总数')).toBeTruthy()
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.getByText('leader')).toBeTruthy()
    expect(screen.getAllByText('50%').length).toBeGreaterThan(0)
    expect(screen.getByText('Tokens 花费')).toBeTruthy()
    expect(screen.getByText('160')).toBeTruthy()
    expect(screen.getByRole('img',{name:'Workflow 创建趋势'})).toBeTruthy()
  })

  it('keeps the basic overview usable when statistics fail',async()=>{
    vi.stubGlobal('fetch',vi.fn(()=>Promise.reject(new Error('统计服务不可用'))))
    render(<Overview project={{id:7,name:'订单服务',repositoryUrl:'repo',ciStatus:'CI_REQUIRED'}} workflows={[{id:21,status:'IN_PROGRESS',title:'进行中工作流'}]}/>)

    expect(await screen.findByText('统计服务不可用')).toBeTruthy()
    expect(screen.getByText('进行中工作流')).toBeTruthy()
    expect(screen.getByText('进行中 Workflow')).toBeTruthy()
  })
})

describe('leader call logs',()=>{
  afterEach(()=>{
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows the summary and expandable request and feedback details',async()=>{
    const response={summary:{totalCalls:2,succeededCalls:1,failedCalls:1,runningCalls:0,totalTokens:160,totalDurationMs:1250},records:[{id:51,agentRunId:41,workflowId:21,attemptNo:1,provider:'openai',model:'gpt-test',runType:'GENERATE_DESIGN',status:'SUCCEEDED',durationMs:120,totalTokens:20,createdAt:'2026-09-14T01:00:00Z',request:{input:'safe'},response:{usage:{total_tokens:20}}}]}
    const fetchMock=vi.fn(()=>okResponse(response))
    vi.stubGlobal('fetch',fetchMock)
    render(<CallLogs project={{id:7}}/>)

    expect(await screen.findByText('调用汇总')).toBeTruthy()
    expect(screen.getByText('总 Tokens')).toBeTruthy()
    expect(screen.getByText('GENERATE_DESIGN')).toBeTruthy()
    expect(screen.getByText(/safe/)).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledWith('/api/projects/7/agent-call-logs',expect.any(Object))
  })
})
