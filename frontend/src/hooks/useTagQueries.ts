import { useQuery } from '@tanstack/react-query'
import { tagKeys } from '../api/queryKeys'
import { fetchTags, suggestTags } from '../api/tags'
import type { TagKind } from '../api/types'

/** 标签集合几十条量级、变化极少(`queryKeys.ts` 里 `clientKeys` 同款约定),给一个较长的 staleTime。 */
const TAG_STALE_TIME_MS = 10 * 60_000

/** 联想结果的缓存时长。比列表短得多:它跟着用户输入变,缓存久了反而在标签刚被建出来时给出过期结果。 */
const SUGGEST_STALE_TIME_MS = 30_000

/** 关键词短于这个长度不发请求。一个字符的联想会把几乎所有词条捞回来,既没帮助又白打一次库。 */
const MIN_SUGGEST_KEYWORD_LENGTH = 2

/** `GET /tags`,`kind` 缺省时返回 CURATED + FREE 全量。 */
export function useTags(kind?: TagKind) {
  return useQuery({
    queryKey: tagKeys.list(kind),
    queryFn: () => fetchTags(kind),
    staleTime: TAG_STALE_TIME_MS,
  })
}

/**
 * `GET /tags/suggest`,输入联想(业务建模文档 5.7:防标签爆炸的主力)。
 *
 * <p>关键词不足两个字符时整个 query 关掉,而不是发一个必然无用的请求 —— 文档里"在用户敲第二个字时
 * 就把已有词条推到面前"这句话,阈值就是从这来的。
 *
 * <p><b>调用方负责防抖。</b> 这里只做长度门槛:防抖是"什么时候把值交给我"的问题,属于输入组件;
 * 塞进 hook 会让它对每个使用场景强加同一个节奏。
 */
export function useTagSuggestions(keyword: string, kind?: TagKind) {
  const trimmed = keyword.trim()
  return useQuery({
    queryKey: tagKeys.suggest(`${kind ?? 'ALL'}:${trimmed}`),
    queryFn: () => suggestTags(trimmed, kind),
    enabled: trimmed.length >= MIN_SUGGEST_KEYWORD_LENGTH,
    staleTime: SUGGEST_STALE_TIME_MS,
  })
}
