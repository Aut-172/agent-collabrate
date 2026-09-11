import React, {useEffect, useMemo, useState} from 'react'
import {createRoot} from 'react-dom/client'
import {Activity, Bell, CheckCircle2, CircleAlert, Eye, FileText, LayoutDashboard, LogOut, Menu, Pencil, Plus, RefreshCw, UserPlus, Users, Workflow as WorkflowIcon, X, XCircle} from 'lucide-react'
import './styles.css'

const columns=[['TODO','待处理'],['ASSIGNED','已分配'],['IN_PROGRESS','开发中'],['BLOCKED','阻塞'],['DELIVERY_SUBMITTED','待交付'],['CI_RUNNING','CI 中'],['DONE','已完成'],['CANCELLED_OR_FAILED','已取消/失败']]
const api=async(path,opts={})=>{const token=localStorage.getItem('ac_token');const r=await fetch(path,{...opts,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})}});if(r.status===401)throw Error('登录已过期');if(!r.ok){let m=`请求失败 (${r.status})`;try{m=(await r.json()).message||m}catch{}throw Error(m)}return r.status===204?null:r.json()}
const stateLabel=s=>({CI_NOT_CONFIGURED:'未配置',CI_REQUIRED:'已启用门禁',TODO:'待处理',ASSIGNED:'已分配',IN_PROGRESS:'开发中',BLOCKED:'阻塞',DELIVERY_SUBMITTED:'待交付',CI_RUNNING:'CI 中',DONE:'已完成',CANCELLED:'已取消',FAILED:'失败',CURRENT:'当前',STALE:'已过期'}[s]||s||'未知')
const roleLabel=role=>({LEADER:'Leader',MEMBER:'Member'}[role]||role||'未知身份')
const effortLevels={HIGH:{label:'高',availability:'FULL_TIME',capacity:32},MEDIUM:{label:'中',availability:'PART_TIME',capacity:20},LOW:{label:'低',availability:'LIMITED',capacity:8}}
const effortFromProfile=(availability,capacity)=>availability==='FULL_TIME'?'HIGH':availability==='PART_TIME'?'MEDIUM':availability==='LIMITED'?'LOW':capacity==null?'MEDIUM':capacity>=28?'HIGH':capacity>=14?'MEDIUM':'LOW'
const effortLabel=(availability,capacity)=>effortLevels[effortFromProfile(availability,capacity)].label
const tone=s=>/DONE|PASSED|CURRENT|SUCCEEDED/.test(s)?'good':/BLOCKED|FAILED|CANCELLED|STALE/.test(s)?'bad':/RUNNING|ASSIGNED|SUBMITTED|PROPOSED/.test(s)?'info':'neutral'
function Status({value}){return <span className={`status ${tone(value)}`}>{/DONE|PASSED|SUCCEEDED/.test(value)?<CheckCircle2 size={13}/>:/BLOCKED|FAILED|CANCELLED/.test(value)?<CircleAlert size={13}/>:<Activity size={13}/>} {stateLabel(value)}</span>}
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
export function App(){
  const [user,setUser]=useState(null)
  const [projects,setProjects]=useState([])
  const [project,setProject]=useState(null)
  const [workflows,setWorkflows]=useState([])
  const [page,setPage]=useState('overview')
  const [mobile,setMobile]=useState(false)
  const [error,setError]=useState('')
  const [createProjectOpen,setCreateProjectOpen]=useState(false)

  useEffect(()=>{if(localStorage.getItem('ac_token'))load()},[])

  async function load(){
    try{
      const [ps,ws,me]=await Promise.all([api('/api/projects'),api('/api/workflows'),api('/api/me')])
      const projectList=(Array.isArray(ps)?ps:[]).filter(item=>item&&typeof item==='object')
      const workflowList=(Array.isArray(ws)?ws:[]).filter(item=>item&&typeof item==='object')
      setProjects(projectList)
      setProject(current=>current&&projectList.some(item=>item.id===current.id)?current:(projectList[0]||null))
      setWorkflows(workflowList)
      setUser(me&&typeof me==='object'?me:{username:'当前用户'})
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

  if(!user)return <Login onLogin={result=>{setUser({userId:result.userId,username:result.username});load()}}/>

  const nav=[['overview','项目概览',LayoutDashboard],['workflows','工作流',WorkflowIcon],['board','任务看板',LayoutDashboard],['members','项目成员',Users],['notifications','通知',Bell],['audit','审计日志',FileText]]
  const pages={
    overview:<Overview project={project} workflows={workflows}/>,
    workflows:<Workflows workflows={workflows} onBoard={()=>setPage('board')}/>,
    board:<Board workflows={workflows}/>,
    members:<Members project={project} user={user}/>,
    notifications:<Notifications/>,
    audit:<Audit project={project}/>
  }

  return <div className="shell"><header className="top"><button className="icon mobile" onClick={()=>setMobile(!mobile)} aria-label="打开导航"><Menu size={20}/></button><strong className="brand">Agent Collaborate</strong><span className="heading">{nav.find(n=>n[0]===page)?.[1]}</span><select value={project?.id||''} disabled={!projects.length} aria-label="当前项目" onChange={e=>setProject(projects.find(p=>String(p.id)===e.target.value)||null)}>{!projects.length&&<option value="">暂无项目</option>}{projects.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select><button className="primary top-create" onClick={()=>setCreateProjectOpen(true)} aria-label="创建项目" title="创建项目"><Plus size={16}/><span>创建项目</span></button><button className="icon refresh" onClick={load} aria-label="刷新"><RefreshCw size={17}/></button><button className="user" onClick={logout}><LogOut size={15}/>{user.username}</button></header><aside className={`side ${mobile?'open':''}`}><nav>{nav.map(([key,label,Icon])=><button key={key} className={page===key?'active':''} onClick={()=>{setPage(key);setMobile(false)}}><Icon size={17}/><span>{label}</span></button>)}</nav><footer>状态以服务端事实为准<br/><small>MVP 管理工作台</small></footer></aside><main className="content">{error&&<div className="alert"><CircleAlert size={16}/>{error}<button className="icon" onClick={()=>setError('')}><XCircle size={15}/></button></div>}<PageErrorBoundary resetKey={page}>{pages[page]||pages.overview}</PageErrorBoundary></main>{createProjectOpen&&<CreateProjectDialog onClose={()=>setCreateProjectOpen(false)} onCreated={projectCreated}/>}</div>
}
function Overview({project,workflows}){const active=workflows.filter(w=>!['DONE','CANCELLED','FAILED'].includes(w.status));return <><div className="page-head"><div><h1>{project?.name||'项目概览'}</h1><p>{project?.repositoryUrl||'请选择项目'}</p></div><Status value={project?.ciStatus}/></div><div className="metrics"><Metric label="进行中 Workflow" value={active.length}/><Metric label="项目 CI" value={stateLabel(project?.ciStatus)}/><Metric label="阻塞任务" value="--"/><Metric label="最近同步" value="--"/></div><section className="panel"><h2>当前工作流</h2>{active.length?active.map(w=><div className="list-row" key={w.id}><div><strong>{w.title}</strong><small>{w.intentLevel} · {w.completionMode}</small></div><Status value={w.status}/></div>):<Empty text="暂无进行中的 Workflow"/>}</section></>}
function Metric({label,value}){return <div className="metric"><small>{label}</small><strong>{value}</strong></div>}
function Workflows({workflows,onBoard}){return <><div className="page-head"><div><h1>工作流</h1><p>意图、文档版本和交付状态</p></div></div><section className="panel">{workflows.length?workflows.map(w=><div className="list-row" key={w.id}><div><strong>{w.title}</strong><small>{w.intentLevel} · {w.description}</small></div><Status value={w.status}/><button className="button" onClick={onBoard}>看板</button></div>):<Empty text="暂无 Workflow"/>}</section></>}
function Board({workflows}){const [data,setData]=useState(null);const wf=workflows[0];useEffect(()=>{if(wf)api(`/api/workflows/${wf.id}/board`).then(setData).catch(()=>{})},[wf?.id]);const groups=useMemo(()=>Object.fromEntries((data?.columns||[]).map(c=>[c.key,c.cards])),[data]);return <><div className="page-head"><div><h1>任务看板</h1><p>只读聚合 · 看板不直接修改状态</p></div></div><div className="board-scroll"><div className="board">{columns.map(([key,label])=><section className="column" key={key}><h3>{label}<b>{(groups[key]||[]).length}</b></h3>{(groups[key]||[]).map(t=><article className="task" key={t.id}><strong>{t.externalKey||t.taskKey} · {t.title}</strong><Status value={t.status}/><small>负责人：{t.assignee?.username||'未分配'}</small><small>任务包 v{t.currentPackageVersion||'-'} · {t.branchName||'无分支'}</small><small>CI：{t.ciStatus||'--'} · {t.updatedAt||'--'}</small></article>)}{!(groups[key]||[]).length&&<Empty text="暂无任务"/>}</section>)}</div></div></>}
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
function Notifications(){const [items,setItems]=useState([]);const refresh=()=>api('/api/notifications').then(setItems).catch(()=>{});useEffect(refresh,[]);return <><div className="page-head"><div><h1>通知</h1><p>当前用户的任务、阻塞和交付提醒</p></div></div><section className="panel">{items.length?items.map(n=><div className={`list-row ${n.readAt?'':'unread'}`} key={n.id}><div><strong>{n.title}</strong><small>{n.content} · {n.createdAt}</small></div>{n.readAt?<Status value="CURRENT"/>:<button className="button" onClick={()=>api(`/api/notifications/${n.id}/read`,{method:'POST'}).then(refresh)}>标记已读</button>}</div>):<Empty text="暂无通知"/>}</section></>}
function Audit({project}){const path=project?`/api/audit-logs?projectId=${project.id}&page=0&size=50`:'';const [state]=useProjectCollection(project?.id,path,response=>Array.isArray(response)?response:response?.content);return <><div className="page-head"><div><h1>审计日志</h1><p>项目级时间线，按最新时间排序</p></div></div><section className="panel">{!project?<Empty text="暂无项目，创建项目后可查看审计日志"/>:state.loading?<Empty text="正在加载审计日志"/>:state.error?<Empty text={`无法加载审计日志：${state.error}`}/>:state.items.length?state.items.map(a=><div className="audit" key={a.id}><time>{formatTime(a.createdAt)}</time><div><strong>{a.action||'未知操作'} · {a.entityType||'未知实体'} #{a.entityId??'-'}</strong><small>操作人：{a.actorUserId||'系统'}</small></div></div>):<Empty text="暂无审计记录"/>}</section></>}
function formatTime(value){if(!value)return '时间未知';const date=new Date(value);return Number.isNaN(date.getTime())?'时间未知':date.toLocaleString()}
function Empty({text}){return <div className="empty">{text}</div>}
class PageErrorBoundary extends React.Component{constructor(props){super(props);this.state={error:null}}static getDerivedStateFromError(error){return {error}}componentDidUpdate(previousProps){if(previousProps.resetKey!==this.props.resetKey&&this.state.error)this.setState({error:null})}componentDidCatch(error){console.error('内容页面渲染失败',error)}render(){return this.state.error?<section className="panel"><Empty text="该页面暂时无法显示，请切换栏目后重试"/></section>:this.props.children}}
class AppErrorBoundary extends React.Component{constructor(props){super(props);this.state={error:null}}static getDerivedStateFromError(error){return {error}}componentDidCatch(error){console.error('前端页面渲染失败',error)}render(){return this.state.error?<main className="login"><section className="login-card"><div className="logo">AC</div><h1>页面暂时无法显示</h1><p className="error">请刷新页面后重试。</p><button className="primary wide" onClick={()=>location.reload()}>刷新页面</button></section></main>:this.props.children}}
const rootElement=document.getElementById('root')
if(rootElement)createRoot(rootElement).render(<AppErrorBoundary><App/></AppErrorBoundary>)
