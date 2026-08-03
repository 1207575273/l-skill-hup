/**
 * skill 包体积 / 结构上限。
 *
 * 数值一律取自业务建模文档(`docs/20260802224934_业务建模与接口契约.md`)5.2
 * "解包上限"那张表,**照抄,不允许为了让某个测试好写而改动这里的值**——上限是
 * 产品决策,不是前端能单方面拍板的,真要改先改文档表格,再回来同步这七个数字。
 *
 * 前后端各自独立实现同一套数值(语言不同,没有共享常量的技术手段),靠这份文档
 * 表格保持一致,不是靠共享代码。改这里时同时去核对 `backend` 的 `application-*.yml`
 * 与校验代码有没有跟着改。
 */

export const SKILL_PACKAGE_LIMITS = {
  /** 上传体积(multipart),已在 `application-*.yml`,这里只是照抄,不代表前端会去改它。 */
  MULTIPART_UPLOAD_MAX_BYTES: 32 * 1024 * 1024,
  /** 解压后总大小上限。 */
  UNPACKED_TOTAL_MAX_BYTES: 64 * 1024 * 1024,
  /** 条目数上限。 */
  MAX_ENTRY_COUNT: 500,
  /** 单个文件大小上限。 */
  SINGLE_FILE_MAX_BYTES: 16 * 1024 * 1024,
  /** 目录深度上限。 */
  MAX_DIRECTORY_DEPTH: 8,
  /** 单条路径长度上限,与 `artifact_key` 列宽一致。 */
  MAX_PATH_LENGTH: 255,
  /** 压缩比上限:解压后总大小 / 压缩包原始字节数,超过判定为 zip 炸弹。 */
  MAX_COMPRESSION_RATIO: 100,
} as const

/** 打包后的 zip 是否超过 multipart 上传上限,供打包完成后做最后一道"能不能传"的判断。 */
export function exceedsMultipartUploadLimit(sizeBytes: number): boolean {
  return sizeBytes > SKILL_PACKAGE_LIMITS.MULTIPART_UPLOAD_MAX_BYTES
}

/**
 * 压缩比是否超限。只在"拖入 zip"形态下有意义 —— 选文件夹 / 单文件两种形态在打包前
 * 不存在"压缩后大小"这个概念,调用方不提供 `originalArchiveBytes` 时不做这项检查。
 */
export function exceedsCompressionRatioLimit(
  unpackedBytes: number,
  originalArchiveBytes: number,
): boolean {
  if (originalArchiveBytes <= 0) {
    return false
  }
  return unpackedBytes / originalArchiveBytes > SKILL_PACKAGE_LIMITS.MAX_COMPRESSION_RATIO
}
