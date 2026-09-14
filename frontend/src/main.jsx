import React, {useEffect, useMemo, useState} from 'react'
import {createRoot} from 'react-dom/client'
import {Activity, Bell, CheckCircle2, CircleAlert, Copy, Download, Ellipsis, Eye, FileText, LayoutDashboard, LogOut, Menu, Pencil, Plus, RefreshCw, UserPlus, Users, Workflow as WorkflowIcon, X, XCircle} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import './styles.css'

const columns=[['TODO','待处理'],['ASSIGNED','已分配'],['IN_PROGRESS','开发中'],['BLOCKED','阻塞'],['DELIVERY_SUBMITTED','待交付'],['CI_RUNNING','CI 中'],['DONE','已完成'],['CANCELLED_OR_FAILED','已取消/失败']]
const api=async(path,opts={})=>{const token=localStorage.getItem('ac_token');const r=await fetch(path,{...opts,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})}});if(r.status===401)throw Error('登录已过期');if(!r.ok){let m=`请求失败 (${r.status})`;try{const body=await r.json();m=body.message||m;if(body.code)m+=` [${body.code}]`;if(body.details)m+=`：${typeof body.details==='string'?body.details:JSON.stringify(body.details)}`}catch{}throw Error(m)}return r.status===204?null:r.json()}
export const stateLabel=s=>({
  INTENT:'Intent Proposed',DESIGN_PROPOSED:'Design Proposed',DESIGN_CONFIRMED:'Design Confirmed',
  SPEC_PROPOSED:'Spec Proposed',SPEC_CONFIRMED:'Spec Confirmed',BUILD_PLAN_PROPOSED:'Build Plan Proposed',
  PLAN_APPROVED:'Build Plan Approved',TASKS_READY:'Tasks Ready',IN_PROGRESS:'In Progress',
  DELIVERY_SUBMITTED:'Delivery Submitted',CI_RUNNING:'CI Running',CI_PASSED:'CI Passed',
  READY_TO_CLOSE:'Ready to Close',DONE:'Done',CANCELLED:'Cancelled',FAILED:'Failed',
  CI_NOT_CONFIGURED:'CI Not Configured',CI_REQUIRED:'CI Required',TODO:'To Do',ASSIGNED:'Assigned',
  BLOCKED:'Blocked',QUEUED:'Queued',RUNNING:'Running',SUCCEEDED:'Succeeded',UNKNOWN:'Unknown',
  CURRENT:'Current',STALE:'Stale',OPEN:'Open',RESOLVED:'Resolved'
}[s]||s||'Unknown')
const ciConclusionLabel=value=>({NO_CHECK_RUNS:'当前 Commit 尚无 CI 检查',IN_PROGRESS:'CI 正在运行',SUCCESS:'CI 已通过',FAILURE:'CI 未通过'}[value]||value||'等待结果')
const ciConclusionHint=value=>value==='NO_CHECK_RUNS'?'请确认该 Commit 已包含 .github/workflows 下的工作流、GitHub Actions 已启用，并在目标仓库中产生检查结果。':value==='Bootstrap 前置任务：CI 尚未建立，无需检查'?'这是初始化工程与 CI 的前置任务；它不要求 CI 检查，加入 .github/workflows 的任务才需要真实 CI。':''
const roleLabel=role=>({LEADER:'Leader',MEMBER:'Member'}[role]||role||'未知身份')
const effortLevels={HIGH:{label:'高',availability:'FULL_TIME',capacity:32},MEDIUM:{label:'中',availability:'PART_TIME',capacity:20},LOW:{label:'低',availability:'LIMITED',capacity:8}}
const effortFromProfile=(availability,capacity)=>availability==='FULL_TIME'?'HIGH':availability==='PART_TIME'?'MEDIUM':availability==='LIMITED'?'LOW':capacity==null?'MEDIUM':capacity>=28?'HIGH':capacity>=14?'MEDIUM':'LOW'
const effortLabel=(availability,capacity)=>effortLevels[effortFromProfile(availability,capacity)].label
const tone=s=>/DONE|PASSED|CURRENT|SUCCEEDED/.test(s)?'good':/BLOCKED|FAILED|CANCELLED|STALE/.test(s)?'bad':/RUNNING|ASSIGNED|SUBMITTED|PROPOSED/.test(s)?'info':'neutral'
function Status({value}){return <span className={`status ${tone(value)}`}>{/DONE|PASSED|SUCCEEDED/.test(value)?<CheckCircle2 size={13}/>:/BLOCKED|FAILED|CANCELLED/.test(value)?<CircleAlert size={13}/>:<Activity size={13}/>} {stateLabel(value)}</span>}
function MarkdownContent({content,format}){
  const markdown=!format||['MARKDOWN','MD','TEXT'].includes(String(format).toUpperCase())
  if(!markdown)return <pre className="document-source">{format==='JSON'?formatJson(content):content}</pre>
  return <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]} components={{a:({children,...props})=><a {...props} target="_blank" rel="noreferrer">{children}</a>}}>{content||''}</ReactMarkdown></div>
}
function downloadTextFile(name,content,mime='text/markdown;charset=utf-8'){
  const url=URL.createObjectURL(new Blob([content||''],{type:mime}))
  const anchor=document.createElement('a')
  anchor.href=url
  anchor.download=name
  anchor.click()
  window.setTimeout(()=>URL.revokeObjectURL(url),0)
}
export function appendResolvedDecisions(content, document, decisions=[]){
  const resolved=(decisions||[]).filter(item=>item.documentVersionId===document?.id&&item.status==='RESOLVED')
  if(!resolved.length)return content||''
  const lines=resolved.map(item=>{
    const option=(item.options||[]).find(value=>value.key===item.selectedOption)
    const label=option?.label||item.selectedOption||'-'
    return `- \`${item.decisionKey}\`：${item.question}；最终选择：${label} (\`${item.selectedOption}\`)`
  })
  return `${content||''}\n\n---\n\n## 已确认决策\n\n${lines.join('\n')}`
}
function formatJson(value){try{return JSON.stringify(JSON.parse(value),null,2)}catch{return value}}
function Login({onLogin}){const [mode,setMode]=useState('login');const [form,setForm]=useState({username:'',password:''});const [error,setError]=useState('');const register=mode==='register';const submit=async e=>{e.preventDefault();setError('');try{const path=register?'/api/auth/register':'/api/auth/login';const r=await api(path,{method:'POST',body:JSON.stringify(form)});localStorage.setItem('ac_token',r.accessToken);onLogin(r)}catch(x){setError(x.message)}};return <main className="login"><form onSubmit={submit} className="login-card"><div className="logo">AC</div><p className="kicker">工程协作平台</p><h1>{register?'创建账号':'登录工作台'}</h1><label>用户名<input required minLength={register?3:1} maxLength={50} autoComplete="username" value={form.username} onChange={e=>setForm({...form,username:e.target.value})}/></label><label>密码<input required minLength={register?8:1} maxLength={128} type="password" autoComplete={register?'new-password':'current-password'} value={form.password} onChange={e=>setForm({...form,password:e.target.value})}/></label><button className="primary wide">{register?'注册并进入':'登录'}</button>{error&&<p className="error">{error}</p>}<button type="button" className="auth-switch" onClick={()=>{setMode(register?'login':'register');setError('')}}>{register?'已有账号？返回登录':'没有账号？注册'}</button></form></main>}
function CreateProjectDialog({onClose,onCreated}){
  const [form,setForm]=useState({name:'',repositoryUrl:'',defaultBranch:'main',gitProvider:'github'})
  const [submitting,setSubmitting]=useState(false)
  const [error,setError]=useState('')
  useEffect(()=>{
    const closeOnEscape=event=>{if(event.key==='Escape'&&!submitting)onClose()}
    document.addEventListener('keydown',closeOnEscape)
    return()=>document.removeEventListener('keydown',closeOnEscape)
  },[onClose,submitting])
  const update=event=>setForm(current=>({...current,[event.target.name]:event.target.value}))
  const submit=async event=>{
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try{
      const created=await api('/api/projects',{method:'POST',body:JSON.stringify(form)})
      if(!created||typeof created!=='object'||created.id==null)throw new Error('创建项目响应无效')
      onCreated(created)
    }catch(requestError){
      setError(requestError instanceof Error?requestError.message:'创建项目失败')
      setSubmitting(false)
    }
  }
  return <div className="modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget&&!submitting)onClose()}}><section className="dialog" role="dialog" aria-modal="true" aria-labelledby="create-project-title"><header><div><h2 id="create-project-title">创建项目</h2><p>登记 GitHub 仓库并初始化项目空间</p></div><button type="button" className="icon" onClick={onClose} disabled={submitting} aria-label="关闭创建项目窗口"><X size={18}/></button></header><form onSubmit={submit}><label>项目名称<input autoFocus required maxLength={100} name="name" value={form.name} onChange={update} placeholder="例如：订单服务"/></label><label>Git 仓库地址<input required maxLength={500} type="url" name="repositoryUrl" value={form.repositoryUrl} onChange={update} placeholder="https://github.com/org/repository"/></label><div className="form-grid"><label>Git Provider<select name="gitProvider" value={form.gitProvider} onChange={update}><option value="github">GitHub</option></select></label><label>默认分支<input required maxLength={100} name="defaultBranch" value={form.defaultBranch} onChange={update} placeholder="main"/></label></div>{error&&<p className="error dialog-error" role="alert">{error}</p>}<footer><button type="button" className="button" onClick={onClose} disabled={submitting}>取消</button><button className="primary" disabled={submitting}>{submitting?'正在创建':'创建项目'}</button></footer></form></section></div>
}

const intentOptions={
  ARCHITECTURE:{label:'Architecture',summary:'系统边界、模块划分和技术约束',path:'Intent -> Design -> Spec -> 子 Intent 规划',policy:'架构基线，不创建代码交付任务，不要求 CI'},
  FEATURE:{label:'Feature',summary:'可独立验收的新能力',path:'Intent -> Design -> Spec -> Build Plan -> 任务分配 -> 开发交付 -> CI',policy:'完整设计、规格、分工、任务与 CI'},
  CHANGE:{label:'Change',summary:'局部代码、配置或行为修改',path:'Intent -> Build Plan -> 任务分配 -> 开发交付 -> CI',policy:'精简实施计划、分工、任务与 CI'}
}
const terminalWorkflowStatuses=new Set(['DONE','CANCELLED','FAILED'])
function CreateWorkflowDialog({project,user,workflows,onClose,onCreated}){
  const [form,setForm]=useState({workflowKind:'STANDARD',title:'',description:'',intentLevel:'FEATURE',pullRequestRequired:true})
  const [role,setRole]=useState(project.createdBy===user.userId?'LEADER':'')
  const [roleLoading,setRoleLoading]=useState(true)
  const [submitting,setSubmitting]=useState(false)
  const [error,setError]=useState('')
  const projectHasNoCi=project.ciStatus==='CI_NOT_CONFIGURED'
  const needsCi=form.intentLevel!=='ARCHITECTURE'
  const activeBootstrap=workflows.find(workflow=>workflow.completionMode==='CI_BOOTSTRAP'&&!terminalWorkflowStatuses.has(workflow.status))
  const canBootstrap=role==='LEADER'&&projectHasNoCi&&!activeBootstrap
  const bootstrapSelected=form.workflowKind==='CI_BOOTSTRAP'
  const selectedIntent=intentOptions[form.intentLevel]

  useEffect(()=>{
    const controller=new AbortController()
    api(`/api/projects/${project.id}/members`,{signal:controller.signal}).then(members=>{
      const own=(Array.isArray(members)?members:[]).find(member=>member.userId===user.userId)
      setRole(own?.projectRole||'MEMBER')
      setRoleLoading(false)
    }).catch(requestError=>{
      if(requestError?.name!=='AbortError'){
        setRole(project.createdBy===user.userId?'LEADER':'MEMBER')
        setRoleLoading(false)
      }
    })
    return()=>controller.abort()
  },[project.id,project.createdBy,user.userId])
  useEffect(()=>{
    if(!canBootstrap&&form.workflowKind==='CI_BOOTSTRAP'){
      setForm(current=>({...current,workflowKind:'STANDARD'}))
    }
  },[canBootstrap,form.workflowKind])
  useEffect(()=>{
    const closeOnEscape=event=>{if(event.key==='Escape'&&!submitting)onClose()}
    document.addEventListener('keydown',closeOnEscape)
    return()=>document.removeEventListener('keydown',closeOnEscape)
  },[onClose,submitting])

  const update=event=>setForm(current=>({...current,[event.target.name]:event.target.type==='checkbox'?event.target.checked:event.target.value}))
  const submit=async event=>{
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try{
      const path=bootstrapSelected?`/api/projects/${project.id}/ci-bootstrap`:`/api/projects/${project.id}/workflows`
      const body=bootstrapSelected
        ?{title:form.title.trim(),description:form.description.trim(),pullRequestRequired:form.pullRequestRequired}
        :{title:form.title.trim(),description:form.description.trim(),intentLevel:form.intentLevel,parentWorkflowId:null,...(form.intentLevel!=='ARCHITECTURE'?{pullRequestRequired:form.pullRequestRequired}:{})}
      const created=await api(path,{method:'POST',body:JSON.stringify(body)})
      if(!created||typeof created!=='object'||created.id==null)throw new Error('创建工作流响应无效')
      onCreated(created)
    }catch(requestError){
      setError(requestError instanceof Error?requestError.message:'创建工作流失败')
      setSubmitting(false)
    }
  }

  const bootstrapHint=roleLoading?'正在确认项目角色':role!=='LEADER'?'只有项目 Leader 可以初始化工程与 CI。':!projectHasNoCi?'项目已经启用 CI；后续 CI 修改请创建普通 Change 工作流。':activeBootstrap?`已有进行中的工程与 CI 初始化工作流：${activeBootstrap.title}`:'建立最小工程骨架、构建测试入口和第一条 CI；任务将直接分配给你。'
  return <div className="modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget&&!submitting)onClose()}}><section className="dialog workflow-dialog" role="dialog" aria-modal="true" aria-labelledby="create-workflow-title"><header><div><h2 id="create-workflow-title">创建工作流</h2><p>{project.name} · {project.repositoryUrl}</p></div><button type="button" className="icon" onClick={onClose} disabled={submitting} aria-label="关闭创建工作流窗口"><X size={18}/></button></header><form onSubmit={submit}><div className="workflow-context"><div><small>默认分支</small><strong>{project.defaultBranch||'未设置'}</strong></div><div><small>项目 CI</small><Status value={project.ciStatus}/></div><div><small>当前身份</small><strong>{roleLoading?'正在确认':roleLabel(role)}</strong></div></div><fieldset className="workflow-kind-fieldset"><legend>工作流类别</legend><label className={form.workflowKind==='STANDARD'?'selected':''}><input type="radio" name="workflowKind" value="STANDARD" checked={form.workflowKind==='STANDARD'} onChange={update}/><span><strong>普通工作流</strong><small>功能、架构、代码或配置变更；后续修改 CI 也属于普通工作流。</small></span></label><label className={bootstrapSelected?'selected':''}><input type="radio" name="workflowKind" value="CI_BOOTSTRAP" checked={bootstrapSelected} onChange={update} disabled={roleLoading||!canBootstrap}/><span><strong>初始化工程与 CI</strong><small>{bootstrapHint}</small></span></label></fieldset><label>工作流标题<input autoFocus required maxLength={200} name="title" value={form.title} onChange={update} placeholder={bootstrapSelected?'例如：建立 Spring Boot 工程与 GitHub Actions':'例如：支持订单批量导出'}/></label><label>目标与验收描述<textarea required maxLength={20000} rows={5} name="description" value={form.description} onChange={update} placeholder={bootstrapSelected?'说明工程骨架、构建测试入口、CI 检查和通过标准。例如：建立 Spring Boot 基础工程，提供 Maven 构建与单元测试命令；GitHub Actions 在 PR 上执行测试且全部通过。':'说明要解决的问题、期望结果、主要约束和验收方式。例如：支持按时间范围导出订单 CSV；导出 1 万条订单应在 30 秒内完成，文件字段与筛选结果一致。'}/></label>{!bootstrapSelected&&<fieldset className="intent-fieldset"><legend>Intent 级别</legend><div className="intent-options">{Object.entries(intentOptions).map(([value,option])=><label className={form.intentLevel===value?'selected':''} key={value}><input type="radio" name="intentLevel" value={value} checked={form.intentLevel===value} onChange={update}/><strong>{option.label}</strong><small>{option.summary}</small></label>)}</div></fieldset>}{(bootstrapSelected||form.intentLevel!=='ARCHITECTURE')&&<label className="checkbox-option"><input type="checkbox" name="pullRequestRequired" checked={form.pullRequestRequired} onChange={update}/><span><strong>要求 Pull Request</strong><small>默认开启；提交交付时必须提供与当前 Commit 匹配的 PR。取消后可仅凭任务分支、Commit 和 CI 完成交付。</small></span></label>}<label>父级 Intent<input readOnly value="无" aria-readonly="true"/></label><section className="workflow-preview" aria-live="polite"><strong>预计流程</strong><p>{bootstrapSelected?'初始化目标 -> Design -> Spec -> Build Plan -> Leader 执行 -> 引导检查 -> 启用 CI 门禁':selectedIntent.path}</p><small>{bootstrapSelected?'仅用于建立初始工程与首条 CI，初始任务固定分配给创建该工作流的 Leader。':selectedIntent.policy}</small></section>{!bootstrapSelected&&projectHasNoCi&&needsCi&&<div className="profile-impact-note"><CircleAlert size={17}/><span>项目尚未初始化工程与 CI；普通 Feature/Change 可以保存，但完成初始化前不能创建可关闭的开发交付。</span></div>}{project.status&&project.status!=='ACTIVE'&&<p className="error dialog-error" role="alert">归档项目不能创建工作流</p>}{error&&<p className="error dialog-error" role="alert">{error}</p>}<footer><button type="button" className="button" onClick={onClose} disabled={submitting}>取消</button><button className="primary" disabled={submitting||(bootstrapSelected&&!canBootstrap)||(project.status&&project.status!=='ACTIVE')}>{submitting?'正在创建':bootstrapSelected?'初始化工程与 CI':'创建工作流'}</button></footer></form></section></div>
}
export function App(){
  const [user,setUser]=useState(null)
  const [projects,setProjects]=useState([])
  const [project,setProject]=useState(null)
  const [workflows,setWorkflows]=useState([])
  const [page,setPage]=useState('overview')
  const [mobile,setMobile]=useState(false)
  const [error,setError]=useState('')
  const [createProjectOpen,setCreateProjectOpen]=useState(false)
  const [createWorkflowOpen,setCreateWorkflowOpen]=useState(false)
  const [selectedWorkflowId,setSelectedWorkflowId]=useState(null)
  const [selectedTaskId,setSelectedTaskId]=useState(null)
  const [taskReturnPage,setTaskReturnPage]=useState('workflow-detail')
  const [notificationItems,setNotificationItems]=useState([])

  useEffect(()=>{if(localStorage.getItem('ac_token'))load()},[])
  useEffect(()=>{if(!user)return;const timer=setInterval(refreshNotifications,15000);return()=>clearInterval(timer)},[user?.userId])

  async function refreshNotifications(){try{const items=await api('/api/notifications');setNotificationItems(Array.isArray(items)?items:[])}catch{}}

  async function load(){
    try{
      const [ps,ws,me,ns]=await Promise.all([api('/api/projects'),api('/api/workflows'),api('/api/me'),api('/api/notifications').catch(()=>[])])
      const projectList=(Array.isArray(ps)?ps:[]).filter(item=>item&&typeof item==='object')
      const workflowList=(Array.isArray(ws)?ws:[]).filter(item=>item&&typeof item==='object')
      setProjects(projectList)
      setProject(current=>current&&projectList.some(item=>item.id===current.id)?current:(projectList[0]||null))
      setWorkflows(workflowList)
      setUser(me&&typeof me==='object'?me:{username:'当前用户'})
      setNotificationItems(Array.isArray(ns)?ns:[])
      setError('')
    }catch(e){
      const message=e instanceof Error?e.message:'加载失败'
      setError(message)
      if(message.includes('过期'))logout()
    }
  }

  function logout(){localStorage.removeItem('ac_token');setUser(null)}
  function projectCreated(created){
    setProjects(current=>[...current.filter(item=>item.id!==created.id),created])
    setProject(created)
    setPage('overview')
    setCreateProjectOpen(false)
  }
  function workflowCreated(created){
    setWorkflows(current=>[created,...current.filter(item=>item.id!==created.id)])
    setCreateWorkflowOpen(false)
    setSelectedWorkflowId(created.id)
    setPage('workflows')
  }

  if(!user)return <Login onLogin={result=>{setUser({userId:result.userId,username:result.username});load()}}/>

  const nav=[['overview','项目概览',LayoutDashboard],['my-tasks','我的任务',CheckCircle2],['workflows','工作流',WorkflowIcon],['board','任务看板',LayoutDashboard],['members','项目成员',Users],['notifications','通知',Bell],['audit','审计日志',FileText]]
  const projectWorkflows=project?workflows.filter(workflow=>workflow.projectId===project.id):[]
  const openTask=(task,returnPage)=>{setSelectedTaskId(task.id);setSelectedWorkflowId(task.workflowId);setTaskReturnPage(returnPage);setPage('task-detail')}
  const pages={
    overview:<Overview project={project} workflows={projectWorkflows}/>,
    'my-tasks':<MyTasks project={project} workflows={projectWorkflows} user={user} onTask={task=>openTask(task,'my-tasks')}/>,
    workflows:<Workflows project={project} workflows={projectWorkflows} onCreate={()=>setCreateWorkflowOpen(true)} onSelect={id=>{setSelectedWorkflowId(id);setPage('workflow-detail')}} onBoard={id=>{setSelectedWorkflowId(id);setPage('board')}}/>,
    'workflow-detail':<WorkflowDetail workflowId={selectedWorkflowId} project={project} user={user} onBack={()=>setPage('workflows')} onTask={id=>openTask({id,workflowId:selectedWorkflowId},'workflow-detail')} onUpdated={updated=>{setWorkflows(current=>current.map(item=>item.id===updated.id?updated:item));api('/api/workflows').then(value=>setWorkflows(Array.isArray(value)?value:[])).catch(()=>{})}}/>,
    'task-detail':<TaskDetail taskId={selectedTaskId} project={project} user={user} onBack={()=>setPage(taskReturnPage)}/>,
    board:<Board workflows={projectWorkflows} workflowId={selectedWorkflowId} onWorkflowSelect={setSelectedWorkflowId}/>,
    members:<Members project={project} user={user}/>,
    notifications:<Notifications items={notificationItems} onRefresh={refreshNotifications}/>,
    audit:<Audit project={project}/>
  }

  const unreadCount=notificationItems.filter(item=>!item.readAt).length
  return <div className="shell"><header className="top"><button className="icon mobile" onClick={()=>setMobile(!mobile)} aria-label="打开导航"><Menu size={20}/></button><strong className="brand">Agent Collaborate</strong><span className="heading">{page==='workflow-detail'?'工作流详情':page==='task-detail'?'任务详情':nav.find(n=>n[0]===page)?.[1]}</span><select value={project?.id||''} disabled={!projects.length} aria-label="当前项目" onChange={e=>{setProject(projects.find(p=>String(p.id)===e.target.value)||null);setCreateWorkflowOpen(false);setSelectedWorkflowId(null);setSelectedTaskId(null);setPage('overview')}}>{!projects.length&&<option value="">暂无项目</option>}{projects.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select><button className="primary top-create" onClick={()=>setCreateProjectOpen(true)} aria-label="创建项目" title="创建项目"><Plus size={16}/><span>创建项目</span></button><button className="icon refresh" onClick={load} aria-label="刷新"><RefreshCw size={17}/></button><button className="user" onClick={logout}><LogOut size={15}/>{user.username}</button></header><aside className={`side ${mobile?'open':''}`}><nav>{nav.map(([key,label,Icon])=><button key={key} className={page===key?'active':''} onClick={()=>{if(key==='board'&&!projectWorkflows.some(item=>String(item.id)===String(selectedWorkflowId)))setSelectedWorkflowId(projectWorkflows[0]?.id??null);setPage(key);setMobile(false)}}><Icon size={17}/><span className="nav-label">{label}{key==='notifications'&&unreadCount>0&&<b className="nav-badge" aria-label={`${unreadCount} 条未读通知`}>{unreadCount>99?'99+':unreadCount}</b>}</span></button>)}</nav><footer>状态以服务端事实为准<br/><small>MVP 管理工作台</small></footer></aside><main className="content">{error&&<div className="alert"><CircleAlert size={16}/>{error}<button className="icon" onClick={()=>setError('')}><XCircle size={15}/></button></div>}<PageErrorBoundary resetKey={page}>{pages[page]||pages.overview}</PageErrorBoundary></main>{createProjectOpen&&<CreateProjectDialog onClose={()=>setCreateProjectOpen(false)} onCreated={projectCreated}/>} {createWorkflowOpen&&project&&<CreateWorkflowDialog project={project} user={user} workflows={projectWorkflows} onClose={()=>setCreateWorkflowOpen(false)} onCreated={workflowCreated}/>}</div>
}
function Overview({project,workflows}){const active=workflows.filter(w=>!['DONE','CANCELLED','FAILED'].includes(w.status));return <><div className="page-head"><div><h1>{project?.name||'项目概览'}</h1><p>{project?.repositoryUrl||'请选择项目'}{project&&<span className="project-branch">默认分支：{project.defaultBranch||'未设置'}</span>}</p></div><Status value={project?.ciStatus}/></div><div className="metrics"><Metric label="进行中 Workflow" value={active.length}/><Metric label="项目 CI" value={stateLabel(project?.ciStatus)}/><Metric label="阻塞任务" value="--"/><Metric label="最近同步" value="--"/></div><section className="panel"><h2>当前工作流</h2>{active.length?active.map(w=><div className="list-row" key={w.id}><div><strong>{w.title}</strong><small>{w.intentLevel} · {w.completionMode}</small></div><Status value={w.status}/></div>):<Empty text="暂无进行中的 Workflow"/>}</section></>}
function Metric({label,value}){return <div className="metric"><small>{label}</small><strong>{value}</strong></div>}
function MyTasks({project,workflows,user,onTask}){
  const [tasks,setTasks]=useState([]),[loading,setLoading]=useState(false),[error,setError]=useState(''),[filter,setFilter]=useState('OPEN')
  const workflowKey=workflows.map(item=>item.id).join(',')
  useEffect(()=>{
    if(!project){setTasks([]);return}
    if(!workflows.length){setTasks([]);setLoading(false);return}
    const controller=new AbortController()
    let cancelled=false
    setLoading(true)
    setError('')
    const requests=workflows.map(workflow=>api(`/api/workflows/${workflow.id}/tasks`,{signal:controller.signal})
      .then(items=>(Array.isArray(items)?items:[]).map(task=>({...task,workflowTitle:workflow.title}))))
    Promise.all(requests)
      .then(groups=>{if(!cancelled)setTasks(groups.flat().filter(task=>String(task.currentAssignment?.assigneeUserId)===String(user?.userId)))})
      .catch(e=>{if(!cancelled&&e?.name!=='AbortError')setError(e.message)})
      .finally(()=>{if(!cancelled)setLoading(false)})
    return()=>{cancelled=true;controller.abort()}
  },[project?.id,workflowKey,user?.userId])
  const visible=tasks.filter(task=>filter==='ALL'||(filter==='DONE'?['DONE','FAILED','CANCELLED'].includes(task.status):!['DONE','FAILED','CANCELLED'].includes(task.status)))
  return <><div className="page-head"><div><h1>我的任务</h1><p>{project?`${project.name} · 当前分配给你的任务`:'请选择项目后查看任务'}</p></div><div className="segmented" aria-label="任务筛选">{[['OPEN','进行中'],['DONE','已结束'],['ALL','全部']].map(([key,label])=><button key={key} className={filter===key?'active':''} onClick={()=>setFilter(key)}>{label}</button>)}</div></div>{error&&<div className="alert"><CircleAlert size={16}/>{error}</div>}{!project?<Empty text="暂无项目，请先选择项目"/>:loading?<Empty text="正在汇总当前项目任务"/>:<section className="my-task-grid">{visible.length?visible.map(task=><article className="my-task-card" key={task.id} onClick={()=>onTask(task)}><header><div><small>{task.workflowTitle}</small><h2>{task.externalKey} · {task.title}</h2></div><Status value={task.status}/></header><p>{task.description}</p><dl><div><dt>工作量</dt><dd>{task.effortPoints} 点</dd></div><div><dt>分支</dt><dd><code>{task.branchName||'-'}</code></dd></div><div><dt>任务包</dt><dd>v{task.currentPackageVersion||'-'}</dd></div></dl><button className="button">查看任务详情</button></article>):<Empty text={filter==='OPEN'?'当前项目没有分配给你的进行中任务':'没有符合筛选条件的任务'}/>}</section>}</>
}
function Workflows({project,workflows,onCreate,onSelect,onBoard}){return <><div className="page-head"><div><h1>工作流</h1><p>{project?'意图、文档版本和交付状态':'请选择项目后查看工作流'}</p></div>{project&&<button className="primary" onClick={onCreate} disabled={project.status&&project.status!=='ACTIVE'} title={project.status&&project.status!=='ACTIVE'?'归档项目不能创建工作流':'创建工作流'}><Plus size={16}/>创建工作流</button>}</div><section className="panel">{workflows.length?workflows.map(w=><div className="list-row clickable" key={w.id} onClick={()=>onSelect(w.id)}><div><strong>{w.title}</strong><small>{w.intentLevel} · {w.description}</small><small>Pull Request：{w.intentLevel==='ARCHITECTURE'?'不适用':w.pullRequestRequired===false?'不要求':'必须'}</small></div><Status value={w.status}/><button className="button" onClick={event=>{event.stopPropagation();onBoard(w.id)}}>看板</button></div>):<Empty text={project?'暂无 Workflow':'暂无项目，请先创建项目'}/>}</section></>}
function Board({workflows,workflowId,onWorkflowSelect}){const [data,setData]=useState(null);const [error,setError]=useState('');const wf=workflows.find(item=>String(item.id)===String(workflowId))||workflows[0]||null;useEffect(()=>{setData(null);setError('');if(wf){onWorkflowSelect?.(wf.id);api(`/api/workflows/${wf.id}/board`).then(setData).catch(e=>setError(e.message))}},[wf?.id]);const groups=useMemo(()=>Object.fromEntries((data?.columns||[]).map(c=>[c.key,c.cards])),[data]);return <><div className="page-head"><div><h1>任务看板</h1><p>{wf?.title||'当前项目暂无 Workflow'} · 只读聚合</p></div>{workflows.length>0&&<label className="board-workflow-select">Workflow<select aria-label="看板 Workflow" value={wf?.id||''} onChange={e=>onWorkflowSelect?.(e.target.value)}>{workflows.map(item=><option key={item.id} value={item.id}>{item.title}</option>)}</select></label>}</div>{error&&<p className="error">{error}</p>}{!wf?<Empty text="当前项目暂无 Workflow"/>:<div className="board-scroll"><div className="board">{columns.map(([key,label])=><section className="column" key={key}><h3>{label}<b>{(groups[key]||[]).length}</b></h3>{(groups[key]||[]).map(t=><article className="task" key={t.id}><strong>{t.externalKey||t.taskKey} · {t.title}</strong><Status value={t.status}/><small>负责人：{t.assignee?.username||'未分配'}</small><small>任务包 v{t.currentPackageVersion||'-'} · {t.branchName||'无分支'}</small><small>CI：{t.ciStatus||'--'} · {t.updatedAt||'--'}</small></article>)}{!(groups[key]||[]).length&&<Empty text="暂无任务"/>}</section>)}</div></div>}</>}

export function DocumentDecisionPanel({items,busy,canResolve,onResolve}){
  const [selected,setSelected]=useState({})
  if(!items.length)return null
  return <section className="document-decisions" aria-label="待确认决策"><header><div><h4>待确认决策</h4><p>完成必要选择后才能确认当前文档。</p></div><span>{items.filter(item=>item.status==='OPEN').length} 项待处理</span></header>{items.map(item=>{const value=selected[item.id]||item.recommendedOption;return <fieldset className="document-decision" key={item.id} disabled={busy||item.status!=='OPEN'}><legend><code>{item.decisionKey}</code><Status value={item.status}/></legend><p>{item.question}</p><div className="decision-options">{(item.options||[]).map(option=><label key={option.key}><input type="radio" name={`decision-${item.id}`} value={option.key} checked={(item.status==='RESOLVED'?item.selectedOption:value)===option.key} onChange={()=>setSelected(current=>({...current,[item.id]:option.key}))}/><span><strong>{option.label}</strong><small><code>{option.key}</code>{option.key===item.recommendedOption?' · 推荐':''}</small></span></label>)}</div><p className="decision-impact">未确认影响：{item.unresolvedImpact}</p>{item.status==='OPEN'&&canResolve&&<button type="button" className="button" disabled={busy||!value} onClick={()=>onResolve(item.id,value)}>确认选择 {item.decisionKey}</button>}{item.status==='RESOLVED'&&<small className="decision-result">已选择 <code>{item.selectedOption}</code> · {formatTime(item.resolvedAt)}</small>}</fieldset>})}</section>
}

function WorkflowDetail({workflowId,project,user,onBack,onTask,onUpdated}){
  const [workflow,setWorkflow]=useState(null),[documents,setDocuments]=useState([]),[decisions,setDecisions]=useState([]),[granularity,setGranularity]=useState(null),[context,setContext]=useState(null),[inventory,setInventory]=useState(null),[tasks,setTasks]=useState([]),[audits,setAudits]=useState([])
  const [loading,setLoading]=useState(true),[error,setError]=useState(''),[run,setRun]=useState(null),[contextRun,setContextRun]=useState(null),[contextPlanRun,setContextPlanRun]=useState(null),[busy,setBusy]=useState(false)
  const [planEditing,setPlanEditing]=useState(false),[planDraft,setPlanDraft]=useState('')
  const [documentEditing,setDocumentEditing]=useState(null),[documentDraft,setDocumentDraft]=useState(''),[projectMembers,setProjectMembers]=useState([])
  const loadContextData=async projectId=>{if(!projectId)return false;let nextContext=null,nextInventory=null;try{nextContext=await api(`/api/workflows/${workflowId}/code-context`)}catch{}try{nextInventory=await api(`/api/projects/${projectId}/repo-inventory/latest`)}catch{}try{const latestRun=await api(`/api/projects/${projectId}/code-context/runs/latest`);setContextRun({...latestRun,runId:latestRun.id})}catch{setContextRun(null)}setContext(nextContext);setInventory(nextInventory);return Boolean(nextContext&&nextContext.status==='CURRENT'&&nextInventory&&nextInventory.status==='CURRENT')}
  const load=async()=>{if(workflowId==null)return;setLoading(true);setError('');try{const [w,d,decisionItems,g]=await Promise.all([api(`/api/workflows/${workflowId}`),api(`/api/workflows/${workflowId}/documents`),api(`/api/workflows/${workflowId}/decisions`).catch(()=>[]),api(`/api/workflows/${workflowId}/plan-granularity`).catch(()=>({warnings:[]}))]);setWorkflow(w);setDocuments(Array.isArray(d)?d:[]);setDecisions(Array.isArray(decisionItems)?decisionItems:[]);setGranularity(g);const activeRuns=Array.isArray(w?.activeAgentRuns)?w.activeAgentRuns:[];const latestRuns=Array.isArray(w?.latestAgentRuns)?w.latestAgentRuns:[];const generationTypes=['GENERATE_DESIGN','GENERATE_SPEC','GENERATE_BUILD_PLAN'];const activeGeneration=activeRuns.find(item=>generationTypes.includes(item.runType));const latestGeneration=latestRuns.find(item=>generationTypes.includes(item.runType));const activeContextPlan=activeRuns.find(item=>item.runType==='GENERATE_CODE_CONTEXT_PLAN');const latestContextPlan=latestRuns.find(item=>item.runType==='GENERATE_CODE_CONTEXT_PLAN');const toRun=item=>item?{...item,runId:item.runId??item.id}:null;setRun(toRun(activeGeneration||latestGeneration));setContextPlanRun(toRun(activeContextPlan||latestContextPlan));onUpdated?.(w);try{setTasks(await api(`/api/workflows/${workflowId}/tasks`))}catch{}try{const timeline=await api(`/api/workflows/${workflowId}/audit-logs?page=0&size=50`);setAudits(Array.isArray(timeline)?timeline:timeline?.content||[])}catch{setAudits([])}await loadContextData(w?.projectId)}catch(e){setError(e.message)}finally{setLoading(false)}}
  useEffect(()=>{load();return()=>{}},[workflowId])
  useEffect(()=>{
    const terminal=terminalWorkflowStatuses.has(workflow?.status)
    document.body.classList.toggle('workflow-terminal-context',terminal)
    return()=>document.body.classList.remove('workflow-terminal-context')
  },[workflow?.status])
  useEffect(()=>{if(!project?.id){setProjectMembers([]);return}api(`/api/projects/${project.id}/members`).then(value=>setProjectMembers(Array.isArray(value)?value:[])).catch(()=>setProjectMembers([]))},[project?.id,user?.userId])
  useEffect(()=>{if(!run?.runId)return;let cancelled=false;let timer;const poll=async()=>{try{const r=await api(`/api/agent-runs/${run.runId}`);if(cancelled)return;setRun({...run,...r});if(['QUEUED','RUNNING'].includes(r.status))timer=setTimeout(poll,3500);else if(r.status==='SUCCEEDED')load();}catch(e){if(!cancelled)setError(e.message)}};poll();return()=>{cancelled=true;clearTimeout(timer)}},[run?.runId])
  useEffect(()=>{if(!contextPlanRun?.runId)return;let cancelled=false;let timer;const poll=async()=>{try{const r=await api(`/api/agent-runs/${contextPlanRun.runId}`);if(cancelled)return;setContextPlanRun({...contextPlanRun,...r});if(['QUEUED','RUNNING'].includes(r.status))timer=setTimeout(poll,3500);else if(r.status==='SUCCEEDED'){await load();} }catch(e){if(!cancelled)setError(e.message)}};poll();return()=>{cancelled=true;clearTimeout(timer)}},[contextPlanRun?.runId])
  useEffect(()=>{if(contextPlanRun?.status!=='SUCCEEDED'||!workflow?.projectId)return;let cancelled=false;let timer;let attempts=0;const poll=async()=>{const ready=await loadContextData(workflow.projectId);if(cancelled)return;if(!ready&&attempts++<10)timer=setTimeout(poll,3500)};poll();return()=>{cancelled=true;clearTimeout(timer)}},[contextPlanRun?.status,workflow?.projectId])
  useEffect(()=>{if(!contextRun?.runId)return;let cancelled=false;let timer;const poll=async()=>{try{const r=await api(`/api/projects/${project?.id||workflow?.projectId}/code-context/runs/${contextRun.runId}`);if(cancelled)return;setContextRun({...contextRun,...r});if(['QUEUED','RUNNING'].includes(r.status))timer=setTimeout(poll,3500);else if(r.status==='SUCCEEDED')load();}catch(e){if(!cancelled)setError(e.message)}};poll();return()=>{cancelled=true;clearTimeout(timer)}},[contextRun?.runId])
  const action=async(path,body)=>{if(busy||run?.status==='QUEUED'||run?.status==='RUNNING')return;setBusy(true);setError('');try{const r=await api(path,{method:'POST',...(body?{body:JSON.stringify(body)}:{})});if(r?.runId)setRun({...r,runId:r.runId});else await load()}catch(e){setError(e.message)}finally{setBusy(false)}}
  const syncContext=async()=>{if(!project?.id||contextRun?.status==='QUEUED'||contextRun?.status==='RUNNING')return;setBusy(true);setError('');try{const r=await api(`/api/projects/${project.id}/code-context/sync`,{method:'POST'});setContextRun(r)}catch(e){setError(e.message)}finally{setBusy(false)}}
  const refreshWorkflowContext=async()=>{if(busy||contextPlanRun?.status==='QUEUED'||contextPlanRun?.status==='RUNNING')return;setBusy(true);setError('');try{const r=await api(`/api/workflows/${workflowId}/code-context/refresh`,{method:'POST'});setContextPlanRun(r)}catch(e){setError(e.message)}finally{setBusy(false)}}
  const confirm=type=>{const doc=documents.filter(d=>d.documentType===type).sort((a,b)=>b.versionNo-a.versionNo)[0];if(doc)action(`/api/workflows/${workflowId}/confirm-${type.toLowerCase().replace('_','-')}`,{versionNo:doc.versionNo})}
  const startDocumentEdit=doc=>{setDocumentEditing(doc.documentType);setDocumentDraft(doc.content||'')}
  const cancelDocumentEdit=()=>{setDocumentEditing(null);setDocumentDraft('')}
  const saveDocument=async type=>{setBusy(true);setError('');try{await api(`/api/workflows/${workflowId}/${type.toLowerCase()}`,{method:'PUT',body:JSON.stringify({content:documentDraft})});cancelDocumentEdit();await load()}catch(e){setError(e.message)}finally{setBusy(false)}}
  const resolveDecision=async(decisionId,selectedOption)=>{setBusy(true);setError('');try{await api(`/api/workflows/${workflowId}/decisions/${decisionId}/resolve`,{method:'POST',body:JSON.stringify({selectedOption})});await load()}catch(e){setError(e.message)}finally{setBusy(false)}}
  const savePlan=async()=>{setBusy(true);setError('');try{JSON.parse(planDraft);await api(`/api/workflows/${workflowId}/plan-drafts`,{method:'PUT',body:JSON.stringify({content:planDraft})});setPlanEditing(false);await load()}catch(e){setError(e instanceof SyntaxError?'Build Plan 不是有效 JSON':e.message)}finally{setBusy(false)}}
  const applyGranularity=async payload=>{setBusy(true);setError('');try{await api(`/api/workflows/${workflowId}/plan-granularity`,{method:'POST',body:JSON.stringify(payload)});await load()}catch(e){setError(e.message)}finally{setBusy(false)}}
  const retryGeneration=()=>{if(run?.runId)action(`/api/agent-runs/${run.runId}/retry`)}
  useEffect(()=>{
    const added=[]
    document.querySelectorAll('article.document').forEach(article=>{
      const markdown=article.querySelector('.markdown-body')
      const actions=article.querySelector('.document-actions')
      if(!markdown||!actions||actions.querySelector('.document-download'))return
      const type=article.querySelector('.document-title-mark')?.textContent?.trim()||'document'
      const doc=documents.find(item=>item.documentType===type&&item.content===markdown.textContent)||documents.find(item=>item.documentType===type)
      if(!doc)return
      const button=document.createElement('button')
      button.type='button'
      button.className='button document-download'
      button.title='下载 Markdown 文档'
      button.setAttribute('aria-label',`下载 ${type} 文档`)
      button.textContent='下载文档'
      button.addEventListener('click',()=>downloadTextFile(`${type.toLowerCase()}-v${doc.versionNo}.md`,appendResolvedDecisions(doc.content,doc,decisions)))
      actions.insertBefore(button,actions.firstChild)
      added.push(button)
    })
    return()=>added.forEach(button=>button.remove())
  },[documents,documentEditing])
  if(workflowId==null)return <Empty text="请先从工作流列表选择一个 Workflow"/>;if(loading&&!workflow)return <Empty text="正在加载 Workflow 详情"/>;
  const latest=type=>documents.filter(d=>d.documentType===type).sort((a,b)=>b.versionNo-a.versionNo)[0];const build=latest('BUILD_PLAN');let plan=null;try{plan=build?.content?JSON.parse(build.content):null}catch{}
  const generate=['GENERATE_SPEC','CONFIRM_DESIGN_OR_GENERATE_SPEC'].includes(workflow?.nextAction)?['生成 Spec','/api/workflows/'+workflowId+'/generate-spec']:workflow?.nextAction==='GENERATE_BUILD_PLAN'?['生成 Build Plan','/api/workflows/'+workflowId+'/generate-build-plan']:workflow?.nextAction==='GENERATE_DESIGN'?['生成 Design','/api/workflows/'+workflowId+'/generate-design']:null
  const contextReady=Boolean(context&&context.status==='CURRENT'&&inventory&&inventory.status==='CURRENT')
  const isLeader=projectMembers.some(member=>member.userId===user?.userId&&member.projectRole==='LEADER')||project?.createdBy===user?.userId
  const canResolveDecisions=isLeader||workflow?.createdBy===user?.userId
  const generationLabel=run?.status==='FAILED'?`重新${generate?.[0]||'生成'}`:generate?.[0]
  return <>
    <div className="page-head"><div><button className="button" onClick={onBack}>← 返回工作流</button><h1>{workflow?.title}</h1><p>{workflow?.description}</p></div><Status value={workflow?.status}/></div>
    {error&&<div className="alert"><CircleAlert size={16}/>{error}</div>}
    <section className="detail-grid">
      <div className="panel"><h2>基本信息</h2><div className="detail-meta"><span>Intent：{workflow?.intentLevel}</span><span>健康：<Status value={workflow?.health}/></span><span>下一步：{workflow?.nextAction}</span><span>更新时间：{formatTime(workflow?.updatedAt)}</span></div>{workflow?.status==='READY_TO_CLOSE'&&<button className="primary document-action" onClick={()=>window.confirm('确认关闭当前 Workflow？关闭后不能继续提交交付。')&&action(`/api/workflows/${workflowId}/close`)}>关闭 Workflow</button>}</div>
      <div className="panel"><h2>Code Context</h2><p>Repo Inventory：<Status value={inventory?.status||'缺失'}/> {inventory?.commitSha&&<small>Commit：<code>{inventory.commitSha.slice(0,12)}…</code></small>}</p><p>Code Context：<Status value={context?.status||'缺失'}/></p><p className="context-steps">步骤 1：同步仓库索引；步骤 2：刷新当前 Workflow Code Context。两步都完成后才能生成 Design、Spec 或 Build Plan。</p>{contextRun?.status==='SUCCEEDED'&&!contextReady&&<p className="context-hint" role="status">仓库索引已同步成功，请继续刷新当前 Workflow Code Context。</p>}{!contextReady&&<p className="error">当前 Workflow 的 Code Context 尚未完成，请先完成上面的两步。</p>}<div className="action-row"><button className="primary" onClick={syncContext} disabled={busy||['QUEUED','RUNNING'].includes(contextRun?.status)}>{contextRun?.status==='RUNNING'?'同步仓库索引中…':'同步仓库索引'}</button><button className="button" onClick={refreshWorkflowContext} disabled={busy||!inventory||['QUEUED','RUNNING'].includes(contextPlanRun?.status)}>{contextPlanRun?.status==='RUNNING'?'刷新 Code Context 中…':'刷新当前 Workflow Code Context'}</button></div>{contextRun&&<p>仓库索引同步 Run：<Status value={contextRun.status}/> {contextRun.errorMessage}</p>}{contextPlanRun&&<p>Workflow Code Context Run：<Status value={contextPlanRun.status}/> {contextPlanRun.errorCode&&<span>{contextPlanRun.errorCode}</span>} {contextPlanRun.errorMessage}</p>}</div>
    </section>
    <section className="panel"><h2>AI 生成</h2>{generate?<button className="primary" onClick={()=>action(generate[1])} disabled={busy||!contextReady||['QUEUED','RUNNING'].includes(run?.status)}>{!contextReady?'请先刷新 Code Context':busy?'提交中…':generationLabel}</button>:<span>当前状态无需生成操作</span>}{run&&<div className="run-status"><Status value={run.status}/>{run.errorCode&&<span>{run.errorCode}</span>} {run.errorMessage&&<span>{run.errorMessage}</span>}</div>}</section>
    <section className="panel workflow-documents"><h2>文档</h2>{['DESIGN','SPEC'].map(type=>{
      const doc=latest(type)
      const editing=documentEditing===type
      const docDecisions=doc?decisions.filter(item=>item.documentVersionId===doc.id):[]
      const hasOpen=docDecisions.some(item=>item.status==='OPEN')
      return <article className="document" key={type}><h3><span className="document-title-mark">{type}</span> {doc&&<small>v{doc.versionNo} · {doc.source} · {doc.contentFormat} · {doc.confirmed?'已确认':'未确认'}</small>}</h3>{doc?(editing?<><textarea className="document-editor" aria-label={`编辑 ${type} 文档`} value={documentDraft} onChange={event=>setDocumentDraft(event.target.value)} spellCheck={false}/><div className="action-row"><button className="button" onClick={cancelDocumentEdit} disabled={busy}>取消</button><button className="primary" onClick={()=>saveDocument(type)} disabled={busy||!documentDraft.trim()}>保存文档</button></div></>:<><MarkdownContent content={doc.content} format={doc.contentFormat}/><DocumentDecisionPanel items={docDecisions} busy={busy} canResolve={canResolveDecisions} onResolve={resolveDecision}/><div className="action-row document-actions">{!doc.confirmed&&<button className="button" onClick={()=>startDocumentEdit(doc)}><Pencil size={15}/>编辑</button>}{!doc.confirmed&&<button className="primary" onClick={()=>confirm(type)} disabled={hasOpen} title={hasOpen?'请先完成待确认决策':''}>确认 {type}</button>}</div></>):<Empty text="暂无文档"/>}</article>
    })}</section>
    {build&&<BuildPlanSection workflow={workflow} document={build} plan={plan} warnings={granularity?.warnings||[]} editing={planEditing} draft={planDraft} busy={busy} canApprovePlan={isLeader} onEdit={()=>{setPlanDraft(formatJson(build.content));setPlanEditing(true)}} onDraft={setPlanDraft} onCancel={()=>setPlanEditing(false)} onSave={savePlan} onApprove={reason=>action(`/api/workflows/${workflowId}/approve-plan`,{versionNo:build.versionNo,reason})} onGranularity={applyGranularity} onCreateTasks={()=>action(`/api/workflows/${workflowId}/create-tasks`)}/>}
    <section className="panel"><h2>任务（{tasks.length}）</h2>{tasks.length?tasks.map(t=><div className="list-row clickable" key={t.id} onClick={()=>onTask(t.id)}><div><strong>{t.externalKey} · {t.title}</strong><small>{t.effortPoints} 点 · 分支 {t.branchName||'-'} · 任务包 v{t.currentPackageVersion||'-'}</small></div><Status value={t.status}/><button className="button">查看任务</button></div>):<Empty text="计划批准并创建任务后，将在这里显示任务"/>}</section>
    <section className="panel"><h2>Workflow 时间线</h2>{audits.length?audits.map(item=><div className="audit" key={item.id}><time>{formatTime(item.createdAt)}</time><div><strong>{item.action} · {item.entityType} #{item.entityId}</strong><small>操作人：{item.actorUserId||'系统'}</small></div></div>):<Empty text="暂无 Workflow 审计记录"/>}</section>
  </>
}

function AgentGenerationPanel({generate,run,busy,contextReady,onGenerate,onRetry}){
  return <section className="panel"><h2>AI 生成</h2>{generate?<button className="primary" onClick={onGenerate} disabled={busy||!contextReady||['QUEUED','RUNNING'].includes(run?.status)}>{!contextReady?'请先刷新 Code Context':busy?'提交中…':run?.status==='FAILED'?'重新生成':generate[0]}</button>:<span>当前状态无需生成操作</span>}{run&&<div className="run-status"><Status value={run.status}/>{run.errorCode&&<span>{run.errorCode}</span>} {run.errorMessage&&<span>{run.errorMessage}</span>}{run.status==='FAILED'&&<button className="button" onClick={onRetry} disabled={busy}>重试本次生成</button>}</div>}</section>
}

export function BuildPlanSection({workflow,document,plan,warnings=[],editing,draft,busy,canApprovePlan,onEdit,onDraft,onCancel,onSave,onApprove,onGranularity,onCreateTasks}){
  const [selected,setSelected]=useState([]),[reason,setReason]=useState(''); const taskKeys=(plan?.tasks||[]).map(t=>t.taskKey); const hasWarnings=warnings.length>0;
  useEffect(()=>{setSelected([]);setReason('')},[document.versionNo])
  const toggle=key=>setSelected(v=>v.includes(key)?v.filter(x=>x!==key):[...v,key]);
  return <section className="panel"><div className="section-head"><div><h2>Build Plan</h2><p>v{document.versionNo} · {document.confirmed?'已批准':'待审批'}</p></div>{workflow.status==='BUILD_PLAN_PROPOSED'&&!editing&&<button className="button" onClick={onEdit}><Pencil size={15}/>高级 JSON 编辑</button>}</div>{editing?<><textarea className="json-editor" value={draft} onChange={e=>onDraft(e.target.value)} spellCheck={false}/><div className="action-row"><button className="button" onClick={onCancel} disabled={busy}>取消</button><button className="primary" onClick={onSave} disabled={busy}>保存新版本</button></div></>:plan?<BuildPlanPreview plan={plan}/>:<pre className="document-source">{document.content}</pre>}{workflow.status==='BUILD_PLAN_PROPOSED'&&!editing&&plan?.tasks?.length>1&&<div className="granularity-panel"><h3>任务拆分辅助</h3>{hasWarnings?<>{warnings.map(w=><p key={w.message} className="warning">{w.message}（{(w.taskKeys||[]).join('、')}）</p>)}<p className="warning">保留当前拆分或直接批准此版本时必须填写理由。</p><div className="task-merge-options">{taskKeys.map(key=><label key={key}><input type="checkbox" checked={selected.includes(key)} onChange={()=>toggle(key)}/>{key}</label>)}</div><textarea aria-label="拆分覆盖理由" placeholder="填写保留拆分理由；合并理由可选" maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/><div className="action-row"><button className="button" disabled={busy||selected.length<2} onClick={()=>onGranularity({operation:'MERGE',taskKeys:selected,reason})}>合并选中 Task</button><button className="button" disabled={busy||!reason.trim()} onClick={()=>onGranularity({operation:'KEEP_SPLIT',taskKeys:selected.length?selected:taskKeys,reason})}>保留当前拆分</button></div></>:<p>未发现明显的过细拆分。</p>}</div>}<div className="action-row">{workflow.status==='BUILD_PLAN_PROPOSED'&&!editing&&(canApprovePlan?<button className="primary" onClick={()=>onApprove(reason)} disabled={busy||(hasWarnings&&!reason.trim())}>批准 v{document.versionNo}</button>:<span className="plan-waiting" role="status">等待 Leader 批准 Build Plan</span>)}{workflow.status==='PLAN_APPROVED'&&<button className="primary" onClick={onCreateTasks} disabled={busy}>{workflow.intentLevel==='ARCHITECTURE'?'创建并开启子 Workflow':'创建任务'}</button>}</div></section>
}
function BuildPlanPreview({plan}){
  const assignmentByTask=Object.fromEntries((plan.assignments||[]).map(item=>[item.taskKey,item]))
  return <><div className="plan-summary"><div><small>Intent</small><strong>{plan.intentLevel||'-'}</strong></div><div><small>人员建议</small><strong>{typeof plan.staffingRecommendation==='string'?plan.staffingRecommendation:JSON.stringify(plan.staffingRecommendation||'-')}</strong></div><div><small>任务/子 Intent</small><strong>{(plan.tasks||plan.childIntents||[]).length}</strong></div></div>{(plan.tasks||[]).length>0&&<div className="table-scroll"><table className="data-table"><thead><tr><th>任务</th><th>工作量</th><th>优先级</th><th>负责人</th><th>范围与验收</th></tr></thead><tbody>{plan.tasks.map(task=>{const assignment=assignmentByTask[task.taskKey];return <tr key={task.taskKey}><td><strong>{task.taskKey} · {task.title}</strong><small>{task.description}</small></td><td>{task.effortPoints??'-'} 点</td><td>{task.priority||'-'}</td><td>{assignment?<><strong>User #{assignment.userId}</strong><small>{assignment.fitReason}</small><small>匹配度 {assignment.assignmentScore??'-'}</small></>:'未分配'}</td><td><small>范围：{Array.isArray(task.scope)?task.scope.join('；'):task.scope||'-'}</small><small>验收：{(task.acceptanceCriteria||[]).join('；')}</small><small>验证：{(task.verificationCommands||[]).join('；')}</small></td></tr>})}</tbody></table></div>}{(plan.childIntents||[]).length>0&&<KeyValueBlock title="子 Intent" value={plan.childIntents}/>} {(plan.alternatives||[]).length>0&&<KeyValueBlock title="备选方案" value={plan.alternatives}/>} {(plan.warnings||[]).length>0&&<KeyValueBlock title="风险与警告" value={plan.warnings}/>}</>
}
function jsonTokenClass(value){
  const token=value.trim().replace(/,$/,'')
  if(/^"/.test(token))return 'json-string'
  if(/^-?\d/.test(token))return 'json-number'
  if(/^(true|false)$/.test(token))return 'json-boolean'
  if(token==='null')return 'json-null'
  return 'json-punctuation'
}
function JsonView({value,label}){
  const serialized=JSON.stringify(value,null,2)??String(value)
  return <div className="json-view" role="region" aria-label={`${label} JSON`} tabIndex="0">{serialized.split('\n').map((line,index)=>{
    const property=line.match(/^(\s*)("(?:\\.|[^"\\])*"):(.*)$/)
    let content
    if(property){
      const [,indent,key,remainder]=property
      content=<>{indent}<span className="json-key">{key}</span><span className="json-punctuation">:</span><span className={jsonTokenClass(remainder)}>{remainder}</span></>
    }else{
      const parts=line.match(/^(\s*)(.*)$/)
      content=<>{parts?.[1]}<span className={jsonTokenClass(parts?.[2]||'')}>{parts?.[2]}</span></>
    }
    return <div className="json-line" key={index}><span className="json-line-number" aria-hidden="true">{index+1}</span><code>{content}</code></div>
  })}</div>
}
function KeyValueBlock({title,value,className=''}){if(title==='子 Intent')return <ChildIntentTable items={Array.isArray(value)?value:[]}/>;return <div className={`plan-note ${className}`.trim()}><strong>{title}</strong><JsonView value={value} label={title}/></div>}
export function ChildIntentTable({items}){return <div className="plan-note child-intent-table"><strong>子 Intent</strong><div className="table-scroll"><table className="data-table"><thead><tr><th>标题</th><th>Intent 级别</th><th>目标与说明</th><th>后续流程</th></tr></thead><tbody>{items.map((item,index)=><tr key={`${item.title||'child'}-${index}`}><td><strong>{item.title||'-'}</strong></td><td><span className="status info">{item.intentLevel||'-'}</span></td><td>{item.description||'-'}</td><td><small>创建后作为独立 Workflow</small><small>{item.intentLevel==='CHANGE'?'Build Plan → 任务交付':'Design → Spec → Build Plan → 任务交付'}</small></td></tr>)}</tbody></table></div></div>}

const blockerReasons=[
  ['REQUIREMENT_CLARIFICATION','需求需要澄清'],
  ['SPEC_CONFLICT','规格存在冲突'],
  ['DEPENDENCY','依赖未就绪'],
  ['ENVIRONMENT','环境问题'],
  ['PERMISSION','权限不足'],
  ['CI_FAILURE','CI 失败'],
  ['TASK_PACKAGE_UPDATED','任务包已更新'],
  ['OTHER','其他原因']
]
const blockerReasonLabel=value=>{
  const label=blockerReasons.find(([reason])=>reason===value)?.[1]
  return label?`${label}（${value}）`:value
}
function TaskDetail({ taskId, project, user, onBack }) {
  const [task, setTask] = useState(null),
    [taskPackage, setTaskPackage] = useState(null),
    [deliveries, setDeliveries] = useState([]),
    [blockers, setBlockers] = useState([]),
    [gitOps, setGitOps] = useState([]),
    [ciRuns, setCiRuns] = useState([]),
    [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [deliveryError, setDeliveryError] = useState(""),
    [notice, setNotice] = useState("");
  const [reassign, setReassign] = useState({
    assigneeUserId: "",
    reason: "",
    assignmentScore: "",
  });
  const [blockerForm, setBlockerForm] = useState({
      reasonCode: "REQUIREMENT_CLARIFICATION",
      summary: "",
      details: "",
      question: "",
    }),
    [resolution, setResolution] = useState("");
  const load = async (silent = false) => {
    if (!taskId) return;
    if (!silent) setLoading(true);
    setError("");
    try {
      const nextTask = await api(`/api/tasks/${taskId}`);
      setTask(nextTask);
      const paths = [
        `/api/tasks/${taskId}/packages/current`,
        `/api/tasks/${taskId}/deliveries`,
        `/api/tasks/${taskId}/blockers`,
        `/api/tasks/${taskId}/git-operations`,
        `/api/tasks/${taskId}/ci-runs`,
        project ? `/api/projects/${project.id}/members` : null,
      ];
      const values = await Promise.all(
        paths.map((path) => (path ? api(path).catch(() => null) : null)),
      );
      setTaskPackage(values[0]);
      setDeliveries(Array.isArray(values[1]) ? values[1] : []);
      setBlockers(Array.isArray(values[2]) ? values[2] : []);
      setGitOps(Array.isArray(values[3]) ? values[3] : []);
      setCiRuns(Array.isArray(values[4]) ? values[4] : []);
      setMembers(Array.isArray(values[5]) ? values[5] : []);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    load();
  }, [taskId]);
  useEffect(() => {
    if (!["DELIVERY_SUBMITTED", "CI_RUNNING"].includes(task?.status)) return;
    const timer = setInterval(() => load(true), 5000);
    return () => clearInterval(timer);
  }, [task?.status, taskId]);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 3000);
    return () => clearTimeout(timer);
  }, [notice]);
  const submit = async (path, method = "POST", body, onFailure) => {
    setBusy(true);
    setError("");
    onFailure?.("");
    setNotice("");
    try {
      await api(path, {
        method,
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      setNotice("操作已完成，页面数据已刷新。");
      await load(true);
      return true;
    } catch (e) {
      const message=e instanceof Error?e.message:"操作失败，请稍后重试";
      setError(message);
      onFailure?.(message);
      return false;
    } finally {
      setBusy(false);
    }
  };
  const confirmPackage = () =>
    taskPackage &&
    submit(
      `/api/tasks/${taskId}/packages/${taskPackage.packageVersion}/confirm`,
      "POST",
      { packageId: taskPackage.id, contentHash: taskPackage.contentHash },
    );
  const submitReassign = async (event) => {
    event.preventDefault();
    const ok = await submit(`/api/tasks/${taskId}/assignee`, "PUT", {
      assigneeUserId: Number(reassign.assigneeUserId),
      reason: reassign.reason,
      assignmentScore:
        reassign.assignmentScore === ""
          ? null
          : Number(reassign.assignmentScore),
    });
    if (ok)
      setReassign({ assigneeUserId: "", reason: "", assignmentScore: "" });
  };
  const reportBlocker = async (event) => {
    event.preventDefault();
    const ok = await submit(`/api/tasks/${taskId}/block`, "POST", {
      deliveryId: null,
      ...blockerForm,
      evidence: null,
    });
    if (ok)
      setBlockerForm({
        reasonCode: "REQUIREMENT_CLARIFICATION",
        summary: "",
        details: "",
        question: "",
      });
  };
  const closeBlocker = async (blocker, cancelled) => {
    if (!resolution.trim()) {
      setError("请先填写 Blocker 处理结论");
      return;
    }
    const ok = await submit(
      `/api/tasks/${taskId}/blockers/${blocker.id}/${cancelled ? "cancel" : "resolve"}`,
      "POST",
      { resolution: resolution.trim() },
    );
    if (ok) setResolution("");
  };
  const submitDelivery = async (finalReport) => {
    if (!taskPackage || !finalReport?.git) return;
    const branchName = String(finalReport.git.branchName || "").trim();
    const commitSha = String(finalReport.git.commitSha || "").trim();
    const pullRequestUrl =
      typeof finalReport.git.pullRequestUrl === "string" &&
      finalReport.git.pullRequestUrl.trim()
        ? finalReport.git.pullRequestUrl.trim()
        : null;
    await submit(
      `/api/tasks/${taskId}/delivery`,
      "POST",
      {
        packageId: taskPackage.id,
        packageVersion: taskPackage.packageVersion,
        packageHash: taskPackage.contentHash,
        finalReport,
        branchName,
        commitSha,
        pullRequestUrl,
      },
      setDeliveryError,
    );
  };
  const retryCi = async (runId) => {
    await submit(`/api/tasks/${taskId}/ci-runs/${runId}/retry`);
  };
  const download = (name, content, type = "text/plain", successMessage) => {
    try {
      const url = URL.createObjectURL(new Blob([content], { type }));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = name;
      anchor.click();
      URL.revokeObjectURL(url);
      setError("");
      setNotice(
        successMessage ||
          (name.endsWith(".md")
            ? "任务包 Markdown 文件已开始下载。"
            : name.endsWith(".json")
              ? "结构化任务包 JSON 文件已开始下载。"
              : "文件已开始下载。"),
      );
    } catch (e) {
      setNotice("");
      setError("文件下载失败，请重试。");
    }
  };
  if (!taskId) return <Empty text="请先选择任务" />;
  if (loading && !task) return <Empty text="正在加载任务详情" />;
  if (!task)
    return (
      <>
        <button className="button" onClick={onBack}>
          ← 返回 Workflow
        </button>
        {error && <p className="error">{error}</p>}
      </>
    );
  const assigneeId = task.currentAssignment?.assigneeUserId;
  const assignee = members.find(
    (item) => String(item.userId) === String(assigneeId),
  );
  const own = String(assigneeId) === String(user?.userId);
  const ownMember = members.find(
    (item) => String(item.userId) === String(user?.userId),
  );
  const leader = ownMember?.projectRole === "LEADER";
  const openBlocker = blockers.find((item) => item.status === "OPEN");
  return (
    <>
      <div className="page-head">
        <div>
          <button className="button" onClick={onBack}>
            ← 返回 Workflow
          </button>
          <h1>
            {task.externalKey} · {task.title}
          </h1>
          <p>{task.description}</p>
        </div>
        <Status value={task.status} />
      </div>
      {error && (
        <div className="alert">
          <CircleAlert size={16} />
          {error}
        </div>
      )}
      {notice && (
        <div className="success-alert">
          <CheckCircle2 size={16} />
          {notice}
        </div>
      )}
      <section className="detail-grid">
        <div className="panel">
          <h2>任务信息</h2>
          <dl className="compact-details">
            <div>
              <dt>负责人</dt>
              <dd>{assignee?.username || `User #${assigneeId || "-"}`}</dd>
            </div>
            <div>
              <dt>工作量</dt>
              <dd>{task.effortPoints} 点</dd>
            </div>
            <div>
              <dt>分支</dt>
              <dd>
                <code>{task.branchName || "-"}</code>
              </dd>
            </div>
            <div>
              <dt>来源</dt>
              <dd>
                Plan v{task.sourcePlanVersion} · Spec v
                {task.sourceSpecVersion || "-"}
              </dd>
            </div>
          </dl>
          {task.branchName && (
            <div className="branch-requirement" role="note" aria-label="分支开发要求">
              <CircleAlert size={17} />
              <span>
                请在任务分支 <code>{task.branchName}</code> 上开发、提交并推送。
                {project?.defaultBranch && <>默认分支 <code>{project.defaultBranch}</code> 仅作为基线，请勿直接修改或推送。</>}
              </span>
            </div>
          )}
          {task.planDetails && (
            <>
              <h3>范围与验收</h3>
              <KeyValueBlock title="任务计划" value={task.planDetails} />
            </>
          )}
        </div>
        <div className="panel">
          <h2>当前分配</h2>
          {task.currentAssignment ? (
            <>
              <p>{task.currentAssignment.assignmentReason}</p>
              <small>
                分配 v{task.currentAssignment.assignmentVersion} · 匹配度{" "}
                {task.currentAssignment.assignmentScore ?? "-"} · 画像 v
                {task.currentAssignment.profileVersion}
              </small>
              <KeyValueBlock
                title="工作量快照"
                value={task.currentAssignment.workloadSnapshot}
              />
            </>
          ) : (
            <Empty text="任务尚未分配" />
          )}
          {leader && !["DONE", "FAILED", "CANCELLED"].includes(task.status) && (
            <form className="compact-form" onSubmit={submitReassign}>
              <h3>重新分配</h3>
              <label>
                负责人
                <select
                  required
                  value={reassign.assigneeUserId}
                  onChange={(e) =>
                    setReassign({ ...reassign, assigneeUserId: e.target.value })
                  }
                >
                  <option value="">请选择</option>
                  {members
                    .filter((item) => item.profileCompleted)
                    .map((item) => (
                      <option key={item.userId} value={item.userId}>
                        {item.username}
                      </option>
                    ))}
                </select>
              </label>
              <label>
                原因
                <input
                  required
                  value={reassign.reason}
                  onChange={(e) =>
                    setReassign({ ...reassign, reason: e.target.value })
                  }
                />
              </label>
              <label>
                匹配度（0～1，可选）
                <input
                  type="number"
                  min="0"
                  max="1"
                  step="0.01"
                  value={reassign.assignmentScore}
                  onChange={(e) =>
                    setReassign({
                      ...reassign,
                      assignmentScore: e.target.value,
                    })
                  }
                />
              </label>
              <button className="button" disabled={busy}>
                重新分配
              </button>
            </form>
          )}
        </div>
      </section>
      <TaskPackagePanel
        task={task}
        taskPackage={taskPackage}
        own={own}
        blockerOpen={Boolean(openBlocker)}
        busy={busy}
        onConfirm={confirmPackage}
        onCopy={() =>
          navigator.clipboard
            .writeText(taskPackage?.contentMarkdown || "")
            .then(() => setNotice("任务包已复制到剪贴板。"))
            .catch(() => setError("无法访问剪贴板"))
        }
        onDownloadMarkdown={() =>
          download(
            `${task.externalKey}-v${taskPackage.packageVersion}.md`,
            taskPackage.contentMarkdown,
            "text/markdown",
          )
        }
        onDownloadJson={() =>
          download(
            `${task.externalKey}-v${taskPackage.packageVersion}.json`,
            JSON.stringify(taskPackage.contentJson, null, 2),
            "application/json",
          )
        }
      />
      {task.status === "IN_PROGRESS" && own && (
        <section className="panel">
          <h2>开发操作</h2>
          <form className="form-stack" onSubmit={reportBlocker}>
            <h3>报告 Blocker</h3>
            <div className="form-grid">
              <label>
                原因
                <select
                  value={blockerForm.reasonCode}
                  onChange={(e) =>
                    setBlockerForm({
                      ...blockerForm,
                      reasonCode: e.target.value,
                    })
                  }
                >
                  {blockerReasons.map(([value,label]) => (
                    <option key={value} value={value}>{label}（{value}）</option>
                  ))}
                </select>
              </label>
              <label>
                摘要
                <input
                  required
                  maxLength={500}
                  placeholder="用一句话概括阻塞及影响。例如：支付回调的失败重试规则不明确，重试逻辑无法继续实现。"
                  value={blockerForm.summary}
                  onChange={(e) =>
                    setBlockerForm({ ...blockerForm, summary: e.target.value })
                  }
                />
              </label>
            </div>
            <label>
              详细信息
              <textarea
                rows={3}
                placeholder="说明遇到的现象、已经尝试的方案及影响范围。例如：已检查 Spec v2，仍未找到 retryCount 字段约束，当前无法继续实现重试逻辑。"
                value={blockerForm.details}
                onChange={(e) =>
                  setBlockerForm({ ...blockerForm, details: e.target.value })
                }
              />
            </label>
            <label>
              需要 Leader 回答的问题
              <textarea
                rows={2}
                placeholder="写出需要 Leader 明确回答的问题。例如：失败重试次数固定为 3 次，还是允许按项目配置？"
                value={blockerForm.question}
                onChange={(e) =>
                  setBlockerForm({ ...blockerForm, question: e.target.value })
                }
              />
            </label>
            <button className="primary" disabled={busy}>
              报告阻塞
            </button>
          </form>
        </section>
      )}
      {openBlocker && (
        <section className="panel blocker-panel">
          <h2>当前 Blocker</h2>
          <Status value={openBlocker.status} />
          <h3>
            {blockerReasonLabel(openBlocker.reasonCode)} · {openBlocker.summary}
          </h3>
          <p>{openBlocker.details}</p>
          {openBlocker.question && (
            <blockquote>{openBlocker.question}</blockquote>
          )}
          {leader && (
            <>
              <label>
                处理结论
                <textarea
                  rows={3}
                  value={resolution}
                  onChange={(e) => setResolution(e.target.value)}
                />
              </label>
              <div className="action-row">
                <button
                  className="primary"
                  disabled={busy}
                  onClick={() => closeBlocker(openBlocker, false)}
                >
                  解决并生成新任务包
                </button>
                <button
                  className="button"
                  disabled={busy}
                  onClick={() => closeBlocker(openBlocker, true)}
                >
                  取消 Blocker
                </button>
              </div>
            </>
          )}
        </section>
      )}
      {task.status === "IN_PROGRESS" && own && taskPackage && (
        <>
        <p className="delivery-policy" role="status">{taskPackage.contentJson?.executionPolicy?.git?.createPullRequest==='REQUIRED'?'当前 Workflow 要求 Pull Request；提交时必须填写与 Commit 匹配的 PR URL。':'当前 Workflow 不强制要求 Pull Request；仍必须提交任务分支、Commit 和 CI 结果。'}</p>
        <DeliveryForm
          onSubmit={submitDelivery}
        busy={busy}
        task={task}
        taskPackage={taskPackage}
        serverError={deliveryError}
        onClearError={()=>{setDeliveryError("");setError("")}}
      />
        </>
      )}
      <EvidencePanel deliveries={deliveries} gitOps={gitOps} ciRuns={ciRuns} canRetryCi={own||leader} busy={busy} onRetryCi={retryCi} />
      {blockers.length > 0 && (
        <section className="panel">
          <h2>Blocker 历史</h2>
          {blockers.map((item) => (
            <div className="list-row" key={item.id}>
              <div>
                <strong>
                  {blockerReasonLabel(item.reasonCode)} · {item.summary}
                </strong>
                <small>
                  {item.resolution || item.question || item.details} ·{" "}
                  {formatTime(item.createdAt)}
                </small>
              </div>
              <Status value={item.status} />
            </div>
          ))}
        </section>
      )}
    </>
  );
}

function TaskPackagePanel({task,taskPackage,own,blockerOpen,busy,onConfirm,onCopy,onDownloadMarkdown,onDownloadJson}){
  const [diff,setDiff]=useState(null),[diffError,setDiffError]=useState(''),[diffLoading,setDiffLoading]=useState(false)
  const toggleDiff=async()=>{if(diff){setDiff(null);return}setDiffLoading(true);setDiffError('');try{setDiff(await api(`/api/tasks/${task.id}/packages/diff?from=${taskPackage.packageVersion-1}&to=${taskPackage.packageVersion}`))}catch(e){setDiffError(e.message)}finally{setDiffLoading(false)}}
  const downloadJson=event=>{event.currentTarget.closest('details')?.removeAttribute('open');onDownloadJson()}
  return <section className="panel">
    <div className="section-head"><div><h2>任务包</h2><p>{taskPackage?`v${taskPackage.packageVersion} · ${taskPackage.status}`:'尚未生成'}</p></div>{taskPackage&&<Status value={taskPackage.status}/>}</div>
    {taskPackage?<>
      <div className="package-meta"><span>Hash：<code>{taskPackage.contentHash}</code></span><span>Base Commit：<code>{taskPackage.baseCommit||'-'}</code></span><span>Code Context：#{taskPackage.codeContextVersionId||'-'}</span></div>
      <MarkdownContent content={taskPackage.contentMarkdown} format="MARKDOWN"/>
      {(own||taskPackage.packageVersion>1)&&<div className="action-row package-actions">
        {own&&<><button className="primary" onClick={onCopy} title="复制完整的 Markdown 任务包，可直接交给开发 Agent"><Copy size={15}/>复制任务说明</button><button className="button icon-text" onClick={onDownloadMarkdown} title="下载与“复制任务说明”内容相同的 Markdown 文件"><Download size={15}/>下载任务包 (.md)</button><details className="more-menu"><summary className="icon" aria-label="更多任务包操作" title="更多任务包操作"><Ellipsis size={19}/></summary><div className="more-menu-popover"><button type="button" onClick={downloadJson} title="下载供自动化工具读取的结构化 JSON 任务包"><Download size={15}/>下载结构化任务包 (.json)</button></div></details></>}
        {taskPackage.packageVersion>1&&<button className="button" onClick={toggleDiff} disabled={diffLoading}>{diff?'收起差异':diffLoading?'加载中…':`对比 v${taskPackage.packageVersion-1}`}</button>}
        {own&&['ASSIGNED','BLOCKED'].includes(task.status)&&<button className="primary" onClick={onConfirm} disabled={busy||blockerOpen||taskPackage.status!=='CURRENT'}>{blockerOpen?'等待 Blocker 关闭':task.status==='BLOCKED'?'确认并恢复开发':'确认并开始开发'}</button>}
      </div>}
      {diffError&&<p className="error">{diffError}</p>}
      {diff&&<div className="package-diff"><section><h3>v{diff.fromVersion}</h3><MarkdownContent content={diff.fromMarkdown} format="MARKDOWN"/></section><section><h3>v{diff.toVersion}</h3><MarkdownContent content={diff.toMarkdown} format="MARKDOWN"/></section></div>}
    </>:<Empty text="当前任务没有任务包"/>}
  </section>
}

function createDeliveryReport(task,taskPackage){
  const report={
    schemaVersion:'1.0',taskId:task.externalKey,packageId:taskPackage.id,
    packageVersion:taskPackage.packageVersion,packageHash:taskPackage.contentHash,
    outcome:'READY_FOR_REVIEW',summary:'',changedFiles:[],
    tests:(task.planDetails?.verificationCommands||[]).map(command=>({command,status:'NOT_RUN',summary:''})),
    git:{branchName:task.branchName||'',commitSha:'',pullRequestUrl:null},
    acceptanceCriteria:(task.planDetails?.acceptanceCriteria||[]).map(item=>({criterion:typeof item==='string'?item:JSON.stringify(item),status:'NOT_VERIFIED',evidence:''})),
    unresolvedIssues:[],outOfScopeChanges:[],blockers:[]
  }
  if(taskPackage.codeContextVersionId&&taskPackage.contextPlanId&&taskPackage.baseCommit){report.codeContextVersionId=taskPackage.codeContextVersionId;report.contextPlanId=taskPackage.contextPlanId;report.baseCommitSha=taskPackage.baseCommit}
  return report
}
function cleanDeliveryReport(value){
  const report=JSON.parse(JSON.stringify(value))
  const cleanList=items=>(Array.isArray(items)?items:[]).map(item=>String(item).trim()).filter(Boolean)
  report.summary=String(report.summary||'').trim()
  report.changedFiles=cleanList(report.changedFiles)
  report.unresolvedIssues=cleanList(report.unresolvedIssues)
  report.outOfScopeChanges=cleanList(report.outOfScopeChanges)
  report.blockers=Array.isArray(report.blockers)?report.blockers:[]
  report.tests=(Array.isArray(report.tests)?report.tests:[]).filter(item=>String(item?.command||'').trim()).map(item=>({...item,command:String(item.command).trim(),summary:String(item.summary||'').trim()}))
  report.acceptanceCriteria=Array.isArray(report.acceptanceCriteria)?report.acceptanceCriteria:[]
  report.git={...report.git,branchName:String(report.git?.branchName||'').trim(),commitSha:String(report.git?.commitSha||'').trim(),pullRequestUrl:typeof report.git?.pullRequestUrl==='string'&&report.git.pullRequestUrl.trim()?report.git.pullRequestUrl.trim():null}
  return report
}
const finalReportRequired=['schemaVersion','taskId','packageId','packageVersion','packageHash','outcome','summary','changedFiles','tests','git','acceptanceCriteria','unresolvedIssues','outOfScopeChanges','blockers']
const finalReportAllowed=[...finalReportRequired,'codeContextVersionId','contextPlanId','baseCommitSha']
const record=value=>Boolean(value)&&typeof value==='object'&&!Array.isArray(value)
const extraFields=(value,allowed)=>record(value)?Object.keys(value).filter(key=>!allowed.includes(key)):[]
function normalizeDeliveryReport(value,defaults){
  const report=JSON.parse(JSON.stringify(value)),notices=[]
  const note=message=>{if(!notices.includes(message))notices.push(message)}
  for(const field of ['changedFiles','tests','acceptanceCriteria','unresolvedIssues','outOfScopeChanges','blockers']){
    if(!(field in report)){report[field]=JSON.parse(JSON.stringify(defaults[field]||[]));note(`已补齐 ${field}`)}
  }
  if(record(report.git)&&!('pullRequestUrl' in report.git)){report.git.pullRequestUrl=null;note('已补齐 git.pullRequestUrl: null')}
  if(Array.isArray(report.tests))report.tests.forEach((item,index)=>{
    if(!record(item))return
    const extras=extraFields(item,['command','status','summary'])
    extras.forEach(field=>delete item[field])
    if(extras.length)note(`已忽略 tests[${index}] 的未知字段：${extras.join('、')}`)
  })
  if(record(report.git)){
    const extras=extraFields(report.git,['branchName','commitSha','pullRequestUrl'])
    extras.forEach(field=>delete report.git[field])
    if(extras.length)note(`已忽略 git 的未知字段：${extras.join('、')}`)
  }
  if(Array.isArray(report.acceptanceCriteria))report.acceptanceCriteria.forEach((item,index)=>{
    const extras=extraFields(item,['criterion','status','evidence'])
    extras.forEach(field=>delete item[field])
    if(extras.length)note(`已忽略 acceptanceCriteria[${index}] 的未知字段：${extras.join('、')}`)
  })
  const rootExtras=extraFields(report,finalReportAllowed)
  rootExtras.forEach(field=>delete report[field])
  if(rootExtras.length)note(`已忽略顶层未知字段：${rootExtras.join('、')}`)
  return {report,notices}
}
function validateDeliveryReport(value){
  if(!record(value))return 'Final Report 必须是一个 JSON 对象。'
  const missing=finalReportRequired.filter(field=>!(field in value))
  if(missing.length)return `Final Report 缺少必填字段：${missing.join('、')}。`
  const extras=extraFields(value,finalReportAllowed)
  if(extras.length)return `Final Report 包含不支持的字段：${extras.join('、')}。`
  if(value.schemaVersion!=='1.0')return 'schemaVersion 必须是 "1.0"。'
  if(!['READY_FOR_REVIEW','BLOCKED','FAILED'].includes(value.outcome))return 'outcome 只能是 READY_FOR_REVIEW、BLOCKED 或 FAILED。'
  if(typeof value.taskId!=='string'||!value.taskId.trim())return 'taskId 必须是非空字符串。'
  if(!Number.isInteger(value.packageId)||value.packageId<1||!Number.isInteger(value.packageVersion)||value.packageVersion<1)return 'packageId 和 packageVersion 必须是大于 0 的整数。'
  if(!/^sha256:[a-f0-9]{64}$/.test(value.packageHash||''))return 'packageHash 必须是 sha256: 开头的 64 位小写十六进制哈希。'
  if(typeof value.summary!=='string'||!value.summary.trim())return 'summary 必须是非空字符串。'
  for(const field of ['changedFiles','unresolvedIssues','outOfScopeChanges']){
    if(!Array.isArray(value[field])||value[field].some(item=>typeof item!=='string'||!item.trim()))return `${field} 必须是字符串数组，空列表请填写 []。`
  }
  if(!Array.isArray(value.tests))return 'tests 必须是数组。'
  for(let index=0;index<value.tests.length;index++){
    const item=value.tests[index]
    if(!record(item))return `tests[${index}] 必须是对象。`
    const itemMissing=['command','status','summary'].filter(field=>!(field in item))
    const itemExtras=extraFields(item,['command','status','summary'])
    if(itemMissing.length||itemExtras.length){
      const parts=[]
      if(itemMissing.length)parts.push(`缺少必填字段 ${itemMissing.join('、')}`)
      if(itemExtras.length)parts.push(`包含不支持字段 ${itemExtras.join('、')}`)
      const hint=itemExtras.includes('result')?' 请删除 result，并使用 status：PASSED、FAILED、SKIPPED 或 NOT_RUN。':''
      return `tests[${index}] ${parts.join('，')}。${hint}`
    }
    if(typeof item.command!=='string'||!item.command.trim()||typeof item.summary!=='string'||!item.summary.trim())return `tests[${index}] 的 command 和 summary 必须是非空字符串。`
    if(!['PASSED','FAILED','SKIPPED','NOT_RUN'].includes(item.status))return `tests[${index}].status 只能是 PASSED、FAILED、SKIPPED 或 NOT_RUN。`
  }
  if(!record(value.git))return 'git 必须是对象。'
  const gitMissing=['branchName','commitSha','pullRequestUrl'].filter(field=>!(field in value.git))
  const gitExtras=extraFields(value.git,['branchName','commitSha','pullRequestUrl'])
  if(gitMissing.length)return `git 缺少必填字段：${gitMissing.join('、')}。`
  if(gitExtras.length)return `git 包含不支持的字段：${gitExtras.join('、')}。`
  if(typeof value.git.branchName!=='string'||!value.git.branchName.trim())return 'git.branchName 必须是非空字符串。'
  if(!/^[a-fA-F0-9]{7,64}$/.test(value.git.commitSha||''))return 'git.commitSha 必须是 7～64 位十六进制字符。'
  if(value.git.pullRequestUrl!==null&&typeof value.git.pullRequestUrl!=='string')return 'git.pullRequestUrl 必须是 URL 字符串或 null。'
  if(!Array.isArray(value.acceptanceCriteria))return 'acceptanceCriteria 必须是数组。'
  for(let index=0;index<value.acceptanceCriteria.length;index++){
    const item=value.acceptanceCriteria[index]
    if(!record(item))return `acceptanceCriteria[${index}] 必须是对象。`
    const itemMissing=['criterion','status','evidence'].filter(field=>!(field in item))
    const itemExtras=extraFields(item,['criterion','status','evidence'])
    if(itemMissing.length)return `acceptanceCriteria[${index}] 缺少必填字段：${itemMissing.join('、')}。`
    if(itemExtras.length)return `acceptanceCriteria[${index}] 包含不支持的字段：${itemExtras.join('、')}。`
    if(typeof item.criterion!=='string'||!item.criterion.trim()||typeof item.evidence!=='string')return `acceptanceCriteria[${index}] 的 criterion 必须非空，evidence 必须是字符串。`
    if(!['PASSED','FAILED','NOT_VERIFIED'].includes(item.status))return `acceptanceCriteria[${index}].status 只能是 PASSED、FAILED 或 NOT_VERIFIED。`
  }
  if(!Array.isArray(value.blockers))return 'blockers 必须是数组。'
  const contextFields=['codeContextVersionId','contextPlanId','baseCommitSha']
  const contextCount=contextFields.filter(field=>field in value).length
  if(contextCount!==0&&contextCount!==contextFields.length)return `代码上下文字段必须同时提供：${contextFields.join('、')}。`
  if(contextCount===contextFields.length&&(!Number.isInteger(value.codeContextVersionId)||value.codeContextVersionId<1||!Number.isInteger(value.contextPlanId)||value.contextPlanId<1||!/^[a-fA-F0-9]{7,100}$/.test(value.baseCommitSha||'')))return '代码上下文 ID 必须是大于 0 的整数，baseCommitSha 必须是 7～100 位十六进制字符。'
  return ''
}
function DeliveryForm({onSubmit,busy,task,taskPackage,serverError,onClearError}){
  const initial=()=>createDeliveryReport(task,taskPackage)
  const [mode,setMode]=useState('JSON'),[report,setReport]=useState(initial),[draft,setDraft]=useState(()=>JSON.stringify(initial(),null,2)),[jsonError,setJsonError]=useState(''),[compatibilityNotice,setCompatibilityNotice]=useState('')
  useEffect(()=>{const next=initial();setReport(next);setDraft(JSON.stringify(next,null,2));setJsonError('');setCompatibilityNotice('')},[task.id,taskPackage.id,taskPackage.packageVersion])
  const clearErrors=()=>{setJsonError('');setCompatibilityNotice('');onClearError?.()}
  const switchMode=next=>{
    if(next===mode)return
    if(next==='FORM'){
      try{const parsed=JSON.parse(draft);if(!record(parsed))throw new Error();const normalized=normalizeDeliveryReport(parsed,initial());setReport(normalized.report);setDraft(JSON.stringify(normalized.report,null,2));setCompatibilityNotice(normalized.notices.join('；'));setJsonError('')}
      catch{setJsonError('JSON 格式不正确，修正后才能切换到表单模式。');return}
    }else setDraft(JSON.stringify(cleanDeliveryReport(report),null,2))
    setMode(next)
  }
  const update=(field,value)=>{clearErrors();setReport(current=>({...current,[field]:value}))}
  const updateGit=(field,value)=>{clearErrors();setReport(current=>({...current,git:{...current.git,[field]:value}}))}
  const updateList=(field,value)=>update(field,value.split(/\r?\n/))
  const tests=report.tests?.length?report.tests:[{command:'',status:'NOT_RUN',summary:''}]
  const updateTest=(index,field,value)=>{clearErrors();setReport(current=>{const items=current.tests?.length?[...current.tests]:[{command:'',status:'NOT_RUN',summary:''}];items[index]={...items[index],[field]:value};return {...current,tests:items}})}
  const updateCriterion=(index,field,value)=>{clearErrors();setReport(current=>({...current,acceptanceCriteria:current.acceptanceCriteria.map((item,itemIndex)=>itemIndex===index?{...item,[field]:value}:item)}))}
  const submitReport=event=>{
    event.preventDefault();setJsonError('')
    let parsed
    try{parsed=mode==='JSON'?JSON.parse(draft):report}catch{setJsonError('JSON 格式不正确，请确保只粘贴一个纯 JSON 对象，并检查括号、引号和逗号。');return}
    const normalized=normalizeDeliveryReport(parsed,initial())
    setCompatibilityNotice(normalized.notices.join('；'))
    setReport(normalized.report)
    if(mode==='JSON')setDraft(JSON.stringify(normalized.report,null,2))
    const schemaError=validateDeliveryReport(normalized.report)
    if(schemaError){setJsonError(schemaError);return}
    const next=cleanDeliveryReport(normalized.report)
    if(next.taskId!==task.externalKey||Number(next.packageId)!==Number(taskPackage.id)||Number(next.packageVersion)!==Number(taskPackage.packageVersion)||next.packageHash!==taskPackage.contentHash){setJsonError('Final Report 的任务包标识与当前任务包不一致，请使用页面预填值。');return}
    if(!next.summary){setJsonError('请填写交付摘要 summary。');return}
    if(!next.git?.branchName||!next.git?.commitSha){setJsonError('请填写 git.branchName 和 git.commitSha。');return}
    if(!/^[a-fA-F0-9]{7,64}$/.test(next.git.commitSha)){setJsonError('git.commitSha 必须是 7～64 位十六进制字符。');return}
    const pullRequestRequired=taskPackage?.contentJson?.executionPolicy?.git?.createPullRequest==='REQUIRED'
    if(pullRequestRequired&&!next.git.pullRequestUrl){setJsonError('当前 Workflow 要求 Pull Request，请填写与 Commit 匹配的 PR URL。');return}
    onSubmit(next)
  }
  return <section className="panel delivery-panel"><div className="section-head"><div><h2>提交交付</h2><p>Final Report</p></div><div className="segmented" aria-label="交付填写方式"><button type="button" className={mode==='JSON'?'active':''} onClick={()=>switchMode('JSON')}>JSON</button><button type="button" className={mode==='FORM'?'active':''} onClick={()=>switchMode('FORM')}>表单</button></div></div><form className="form-stack" onSubmit={submitReport}>{mode==='JSON'?<label>Final Report JSON<textarea className="delivery-json-editor" aria-label="Final Report JSON" spellCheck={false} value={draft} onChange={event=>{setDraft(event.target.value);clearErrors()}}/></label>:<><label>交付摘要（顶层 summary）<textarea required rows={3} placeholder="例如：完成支付回调验签和重复通知幂等处理，并补充相关测试。" value={report.summary||''} onChange={event=>update('summary',event.target.value)}/></label><div className="form-grid"><label>任务分支<input required value={report.git?.branchName||''} onChange={event=>updateGit('branchName',event.target.value)}/></label><label>Commit SHA<input required pattern="[a-fA-F0-9]{7,64}" value={report.git?.commitSha||''} onChange={event=>updateGit('commitSha',event.target.value)}/></label></div><label>Pull Request URL（可选）<input type="url" value={report.git?.pullRequestUrl||''} onChange={event=>updateGit('pullRequestUrl',event.target.value)}/></label><label>变更文件（每行一个）<textarea required rows={4} placeholder={'例如：\nsrc/main/java/example/PaymentService.java\nsrc/test/java/example/PaymentServiceTest.java'} value={(report.changedFiles||[]).join('\n')} onChange={event=>updateList('changedFiles',event.target.value)}/></label><div className="criteria-editor delivery-tests"><strong>验证结果（tests）</strong>{tests.map((test,index)=><div className="delivery-result-row" key={`${test.command}-${index}`}><div className="form-grid"><label>验证命令（command）<input value={test.command||''} onChange={event=>updateTest(index,'command',event.target.value)} placeholder="例如：mvn test"/></label><label>验证状态（status）<select value={test.status||'NOT_RUN'} onChange={event=>updateTest(index,'status',event.target.value)}><option value="PASSED">通过（PASSED）</option><option value="FAILED">失败（FAILED）</option><option value="SKIPPED">已跳过（SKIPPED）</option><option value="NOT_RUN">未执行（NOT_RUN）</option></select></label></div><label>验证结果摘要（summary）<input required={Boolean(test.command)} value={test.summary||''} onChange={event=>updateTest(index,'summary',event.target.value)} placeholder="例如：全部 42 项测试通过。"/></label></div>)}</div>{(report.acceptanceCriteria||[]).length>0&&<div className="criteria-editor"><strong>验收结果（acceptanceCriteria）</strong>{report.acceptanceCriteria.map((item,index)=><div className="delivery-result-row" key={`${index}-${item.criterion}`}><strong>{item.criterion}</strong><div className="form-grid"><label>验收状态（status）<select value={item.status||'NOT_VERIFIED'} onChange={event=>updateCriterion(index,'status',event.target.value)}><option value="PASSED">通过（PASSED）</option><option value="FAILED">失败（FAILED）</option><option value="NOT_VERIFIED">未验证（NOT_VERIFIED）</option></select></label><label>验收证据（evidence）<textarea rows={3} value={item.evidence||''} onChange={event=>updateCriterion(index,'evidence',event.target.value)} placeholder="例如：PaymentServiceTest.retryOnFailure 测试通过。"/></label></div></div>)}</div>}<details className="delivery-advanced"><summary>更多交付信息</summary><div className="form-grid"><label>未解决问题（每行一个）<textarea rows={3} value={(report.unresolvedIssues||[]).join('\n')} onChange={event=>updateList('unresolvedIssues',event.target.value)} placeholder="没有则留空"/></label><label>范围外变更（每行一个）<textarea rows={3} value={(report.outOfScopeChanges||[]).join('\n')} onChange={event=>updateList('outOfScopeChanges',event.target.value)} placeholder="没有则留空"/></label></div></details></>}{compatibilityNotice&&<div className="delivery-compat" role="status"><CircleAlert size={16}/><span>提交前已自动兼容：{compatibilityNotice}。</span></div>}{jsonError&&<div className="delivery-error" role="alert"><CircleAlert size={16}/><span>{jsonError}</span></div>}{serverError&&<div className="delivery-error" role="alert"><CircleAlert size={16}/><span>{serverError}</span></div>}<button className="primary" disabled={busy}>{busy?'正在提交…':'提交交付并开始 Git 校验'}</button></form></section>
}

export function EvidencePanel({deliveries,gitOps,ciRuns,canRetryCi,busy,onRetryCi}){return <section className="panel"><h2>交付与验证</h2>{deliveries.length===0?<Empty text="暂无交付记录"/>:deliveries.map(item=><div className="evidence-item" key={item.id}><div><strong>交付 #{item.id}</strong><Status value={item.status}/></div><small><code>{item.commitSha}</code> · {item.branchName} · {formatTime(item.submittedAt)}</small>{item.rejectionReason&&<p className="error">{item.rejectionReason}</p>}</div>)}{gitOps.length>0&&<><h3>Git 校验</h3>{gitOps.map(item=><div className="list-row" key={item.id}><div><strong>{item.operationType} · <code>{item.commitSha}</code></strong><small>{item.errorMessage||`已验证 Commit：${item.verifiedCommitSha||'-'}`}</small></div><Status value={item.status}/></div>)}</>}{ciRuns.length>0&&<><h3>CI Runs</h3>{ciRuns.map(item=><div className="list-row" key={item.id}><div><strong><code>{item.commitSha}</code> · {ciConclusionLabel(item.conclusion)}</strong><small>最近同步：{formatTime(item.lastSyncedAt)}</small>{ciConclusionHint(item.conclusion)&&<small>{ciConclusionHint(item.conclusion)}</small>}{item.detailsUrl&&<a href={item.detailsUrl} target="_blank" rel="noreferrer">查看 CI 详情</a>}</div><Status value={item.status}/>{canRetryCi&&item.status==='UNKNOWN'&&item.conclusion!=='Bootstrap 前置任务：CI 尚未建立，无需检查'&&<button className="button icon-text" disabled={busy} onClick={()=>onRetryCi(item.id)}><RefreshCw size={15}/>重新同步 CI</button>}</div>)}</>}</section>}
const emptyCollectionState={items:[],loading:false,error:''}
function useProjectCollection(projectId,path,selectItems=value=>value){
  const [state,setState]=useState(emptyCollectionState)
  useEffect(()=>{
    if(projectId==null){setState(emptyCollectionState);return}
    const controller=new AbortController()
    setState({items:[],loading:true,error:''})
    api(path,{signal:controller.signal}).then(response=>{
      const items=selectItems(response)
      setState({items:(Array.isArray(items)?items:[]).filter(item=>item&&typeof item==='object'),loading:false,error:''})
    }).catch(error=>{
      if(error?.name!=='AbortError')setState({items:[],loading:false,error:error instanceof Error?error.message:'加载失败'})
    })
    return()=>controller.abort()
  },[projectId,path])
  return [state,setState]
}
function InviteMemberDialog({project,onClose,onAdded}){
  const [username,setUsername]=useState('')
  const [submitting,setSubmitting]=useState(false)
  const [error,setError]=useState('')
  const submit=async event=>{
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try{
      const member=await api(`/api/projects/${project.id}/members`,{method:'POST',body:JSON.stringify({username:username.trim()})})
      if(!member||typeof member!=='object'||member.userId==null)throw new Error('邀请成员响应无效')
      onAdded(member)
    }catch(requestError){
      setError(requestError instanceof Error?requestError.message:'邀请成员失败')
      setSubmitting(false)
    }
  }
  return <div className="modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget&&!submitting)onClose()}}><section className="dialog compact" role="dialog" aria-modal="true" aria-labelledby="invite-member-title"><header><div><h2 id="invite-member-title">邀请成员</h2><p>{project.name}</p></div><button type="button" className="icon" onClick={onClose} disabled={submitting} aria-label="关闭邀请成员窗口"><X size={18}/></button></header><form onSubmit={submit}><label>成员用户名<input autoFocus required minLength={3} maxLength={50} value={username} onChange={event=>setUsername(event.target.value)} autoComplete="off"/></label>{error&&<p className="error dialog-error" role="alert">{error}</p>}<footer><button type="button" className="button" onClick={onClose} disabled={submitting}>取消</button><button className="primary" disabled={submitting}>{submitting?'正在邀请':'邀请成员'}</button></footer></form></section></div>
}
const lines=value=>(Array.isArray(value)?value:[]).join('\n')
const splitLines=value=>value.split(/\r?\n/).map(item=>item.trim()).filter(Boolean)
function ProfileList({label,items}){return <div><dt>{label}</dt><dd>{Array.isArray(items)&&items.length?<ul>{items.map((item,index)=><li key={`${item}-${index}`}>{item}</li>)}</ul>:'未填写'}</dd></div>}
function ProfileDialog({member,editing,onClose,onSaved}){
  const profile=member.capabilityProfile||{}
  const [form,setForm]=useState({
    skills:lines(profile.skills),experience:lines(profile.experience),
    preferredTaskTypes:lines(profile.preferredTaskTypes),limitations:lines(profile.limitations),
    effortLevel:effortFromProfile(member.availability||profile.availability,member.weeklyCapacityPoints??profile.weeklyCapacityPoints)
  })
  const [submitting,setSubmitting]=useState(false)
  const [error,setError]=useState('')
  const update=event=>setForm(current=>({...current,[event.target.name]:event.target.value}))
  const submit=async event=>{
    event.preventDefault()
    setSubmitting(true)
    setError('')
    const skills=splitLines(form.skills)
    const effort=effortLevels[form.effortLevel]
    const payload={summary:skills.length?`技能：${skills.slice(0,3).join('、')}`:'成员能力画像',responsibilities:[],skills,experience:splitLines(form.experience),preferredTaskTypes:splitLines(form.preferredTaskTypes),limitations:splitLines(form.limitations),availability:effort.availability,weeklyCapacityPoints:effort.capacity,notes:''}
    try{
      const saved=await api(`/api/projects/${member.projectId||''}/members/me/profile`,{method:'PUT',body:JSON.stringify(payload)})
      if(!saved||typeof saved!=='object'||saved.userId==null)throw new Error('更新能力画像响应无效')
      onSaved(saved)
    }catch(requestError){
      setError(requestError instanceof Error?requestError.message:'更新能力画像失败')
      setSubmitting(false)
    }
  }
  if(!editing)return <div className="modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}}><section className="dialog" role="dialog" aria-modal="true" aria-labelledby="view-profile-title"><header><div><h2 id="view-profile-title">{member.username} 的能力画像</h2><p>{roleLabel(member.projectRole)} · 版本 {member.profileVersion||0}</p></div><button type="button" className="icon" onClick={onClose} aria-label="关闭能力画像"><X size={18}/></button></header><div className="profile-body">{!member.profileCompleted||!member.capabilityProfile?<Empty text="该成员尚未填写能力画像"/>:<dl className="profile-details"><ProfileList label="技能" items={profile.skills}/><ProfileList label="经验" items={profile.experience}/><ProfileList label="偏好任务" items={profile.preferredTaskTypes}/><ProfileList label="限制" items={profile.limitations}/><div><dt>投入程度</dt><dd>{effortLabel(member.availability||profile.availability,member.weeklyCapacityPoints??profile.weeklyCapacityPoints)}</dd></div></dl>}</div></section></div>
  return <div className="modal-backdrop"><section className="dialog profile-editor" role="dialog" aria-modal="true" aria-labelledby="edit-profile-title"><header><div><h2 id="edit-profile-title">编辑能力画像</h2><p>{member.username} · 保存后生成新版本</p></div><button type="button" className="icon" onClick={onClose} disabled={submitting} aria-label="关闭能力画像编辑窗口"><X size={18}/></button></header><form onSubmit={submit}><div className="form-grid"><label>技能<textarea autoFocus maxLength={20000} name="skills" value={form.skills} onChange={update} rows={4} placeholder={'例如：Java\nPostgreSQL\nReact'}/></label><label>经验<textarea maxLength={20000} name="experience" value={form.experience} onChange={update} rows={4} placeholder={'例如：3 年后端开发\n参与过微服务重构'}/></label><label>偏好任务<textarea maxLength={20000} name="preferredTaskTypes" value={form.preferredTaskTypes} onChange={update} rows={4} placeholder={'例如：后端功能开发\n性能优化'}/></label><label>限制<textarea maxLength={20000} name="limitations" value={form.limitations} onChange={update} rows={4} placeholder={'例如：暂不承担移动端开发\n每周五不可用'}/></label></div><label>投入程度<select name="effortLevel" value={form.effortLevel} onChange={update}><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label><div className="profile-impact-note"><CircleAlert size={17}/><span>能力画像会影响任务分配，并作为最终贡献衡量的参考。</span></div>{error&&<p className="error dialog-error" role="alert">{error}</p>}<footer><button type="button" className="button" onClick={onClose} disabled={submitting}>取消</button><button className="primary" disabled={submitting}>{submitting?'正在保存':'保存画像'}</button></footer></form></section></div>
}
function Members({project,user}){
  const path=project?`/api/projects/${project.id}/members`:''
  const [state,setState]=useProjectCollection(project?.id,path)
  const [inviteOpen,setInviteOpen]=useState(false)
  const [viewing,setViewing]=useState(null)
  const [editing,setEditing]=useState(null)
  const currentMember=state.items.find(member=>String(member.userId)===String(user?.userId))
  const isLeader=currentMember?.projectRole==='LEADER'
  const added=member=>{setState(current=>({...current,items:[...current.items.filter(item=>item.userId!==member.userId),member]}));setInviteOpen(false)}
  const saved=member=>{setState(current=>({...current,items:current.items.map(item=>item.userId===member.userId?member:item)}));setEditing(null)}
  return <><div className="page-head"><div><h1>项目成员</h1><p>角色与能力画像</p></div>{isLeader&&<button className="primary" onClick={()=>setInviteOpen(true)}><UserPlus size={16}/>邀请成员</button>}</div><section className="panel">{!project?<Empty text="暂无项目，请先创建项目"/>:state.loading?<Empty text="正在加载项目成员"/>:state.error?<Empty text={`无法加载项目成员：${state.error}`}/>:state.items.length?state.items.map(member=>{const own=String(member.userId)===String(user?.userId);const profileMember={...member,projectId:project.id};return <div className="list-row member-row" key={member.id??member.userId}><div><strong>{member.username||'未命名用户'}{own&&<span className="self-mark">当前用户</span>}</strong><small>{roleLabel(member.projectRole)} · 画像 v{member.profileVersion??0}</small></div><Status value={member.profileCompleted?'CURRENT':'PENDING'}/><div className="member-actions"><button className="button icon-text" onClick={()=>setViewing(profileMember)}><Eye size={15}/>查看能力画像</button>{own&&<button className="button icon-text" onClick={()=>setEditing(profileMember)}><Pencil size={15}/>编辑能力画像</button>}</div></div>}):<Empty text="该项目暂无成员"/>}</section>{inviteOpen&&<InviteMemberDialog project={project} onClose={()=>setInviteOpen(false)} onAdded={added}/>} {viewing&&<ProfileDialog member={viewing} editing={false} onClose={()=>setViewing(null)}/>} {editing&&<ProfileDialog member={editing} editing onClose={()=>setEditing(null)} onSaved={saved}/>}</>
}
function Notifications({items,onRefresh}){const [error,setError]=useState(''),[busyId,setBusyId]=useState(null);useEffect(()=>{onRefresh?.()},[]);const markRead=async id=>{setBusyId(id);setError('');try{await api(`/api/notifications/${id}/read`,{method:'POST'});await onRefresh?.()}catch(e){setError(e.message)}finally{setBusyId(null)}};return <><div className="page-head"><div><h1>通知</h1><p>任务分配、重新分配、阻塞和交付提醒</p></div><button className="button" onClick={onRefresh}><RefreshCw size={15}/>刷新</button></div>{error&&<div className="alert"><CircleAlert size={16}/>{error}</div>}<section className="panel">{items.length?items.map(n=><div className={`list-row ${n.readAt?'':'unread'}`} key={n.id}><div><strong>{n.title}</strong><small>{n.content} · {formatTime(n.createdAt)}</small>{['TASK_ASSIGNED','TASK_REASSIGNED'].includes(n.type)&&<span className="notification-type">任务分配</span>}</div>{n.readAt?<Status value="CURRENT"/>:<button className="button" disabled={busyId===n.id} onClick={()=>markRead(n.id)}>{busyId===n.id?'处理中…':'标记已读'}</button>}</div>):<Empty text="暂无通知"/>}</section></>}
function Audit({project}){const path=project?`/api/audit-logs?projectId=${project.id}&page=0&size=50`:'';const [state]=useProjectCollection(project?.id,path,response=>Array.isArray(response)?response:response?.content);return <><div className="page-head"><div><h1>审计日志</h1><p>项目级时间线，按最新时间排序</p></div></div><section className="panel">{!project?<Empty text="暂无项目，创建项目后可查看审计日志"/>:state.loading?<Empty text="正在加载审计日志"/>:state.error?<Empty text={`无法加载审计日志：${state.error}`}/>:state.items.length?state.items.map(a=><div className="audit" key={a.id}><time>{formatTime(a.createdAt)}</time><div><strong>{a.action||'未知操作'} · {a.entityType||'未知实体'} #{a.entityId??'-'}</strong><small>操作人：{a.actorUserId||'系统'}</small></div></div>):<Empty text="暂无审计记录"/>}</section></>}
function formatTime(value){if(!value)return '时间未知';const date=new Date(value);return Number.isNaN(date.getTime())?'时间未知':date.toLocaleString()}
function Empty({text}){return <div className="empty">{text}</div>}
class PageErrorBoundary extends React.Component{constructor(props){super(props);this.state={error:null}}static getDerivedStateFromError(error){return {error}}componentDidUpdate(previousProps){if(previousProps.resetKey!==this.props.resetKey&&this.state.error)this.setState({error:null})}componentDidCatch(error){console.error('内容页面渲染失败',error)}render(){return this.state.error?<section className="panel"><Empty text="该页面暂时无法显示，请切换栏目后重试"/></section>:this.props.children}}
class AppErrorBoundary extends React.Component{constructor(props){super(props);this.state={error:null}}static getDerivedStateFromError(error){return {error}}componentDidCatch(error){console.error('前端页面渲染失败',error)}render(){return this.state.error?<main className="login"><section className="login-card"><div className="logo">AC</div><h1>页面暂时无法显示</h1><p className="error">请刷新页面后重试。</p><button className="primary wide" onClick={()=>location.reload()}>刷新页面</button></section></main>:this.props.children}}
const rootElement=document.getElementById('root')
if(rootElement){const root=rootElement.__agentCollaborateRoot||(rootElement.__agentCollaborateRoot=createRoot(rootElement));root.render(<AppErrorBoundary><App/></AppErrorBoundary>)}
