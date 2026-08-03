import { useParams } from 'react-router'

/**
 * 详情页占位。真正的正文渲染、版本列表、认证徽章按展示约定第七章单独实现。
 * 这里只验证路由参数 `:id` 能取到,不发任何请求。
 */
export default function SkillDetailPage() {
  const { id } = useParams<{ id: string }>()
  return <p>skill 详情页占位,id = {id}</p>
}
