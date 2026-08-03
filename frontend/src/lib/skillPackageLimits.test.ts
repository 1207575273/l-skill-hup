import { describe, expect, it } from 'vitest'
import {
  exceedsCompressionRatioLimit,
  exceedsMultipartUploadLimit,
  SKILL_PACKAGE_LIMITS,
} from './skillPackageLimits'

describe('SKILL_PACKAGE_LIMITS', () => {
  it('应与业务建模文档 5.2 解包上限表的七个数值完全一致', () => {
    expect(SKILL_PACKAGE_LIMITS).toEqual({
      MULTIPART_UPLOAD_MAX_BYTES: 32 * 1024 * 1024,
      UNPACKED_TOTAL_MAX_BYTES: 64 * 1024 * 1024,
      MAX_ENTRY_COUNT: 500,
      SINGLE_FILE_MAX_BYTES: 16 * 1024 * 1024,
      MAX_DIRECTORY_DEPTH: 8,
      MAX_PATH_LENGTH: 255,
      MAX_COMPRESSION_RATIO: 100,
    })
  })
})

describe('exceedsMultipartUploadLimit', () => {
  it('应接受恰好达到上限的大小', () => {
    expect(exceedsMultipartUploadLimit(SKILL_PACKAGE_LIMITS.MULTIPART_UPLOAD_MAX_BYTES)).toBe(false)
  })

  it('应拒绝超过上限一个字节的大小', () => {
    expect(exceedsMultipartUploadLimit(SKILL_PACKAGE_LIMITS.MULTIPART_UPLOAD_MAX_BYTES + 1)).toBe(
      true,
    )
  })
})

describe('exceedsCompressionRatioLimit', () => {
  it('应接受恰好等于上限(100:1)的压缩比', () => {
    expect(exceedsCompressionRatioLimit(1000, 10)).toBe(false)
  })

  it('应拒绝超过上限的压缩比', () => {
    expect(exceedsCompressionRatioLimit(1001, 10)).toBe(true)
  })

  it('原始压缩包字节数为 0 时不判定为炸弹,避免除零', () => {
    expect(exceedsCompressionRatioLimit(1000, 0)).toBe(false)
  })
})
