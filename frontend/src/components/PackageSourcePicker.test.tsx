import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SkillUploadInput } from '../lib/skillPackageBuilder'
import { PackageSourcePicker } from './PackageSourcePicker'

function createFile(name: string, content = 'content'): File {
  return new File([content], name)
}

describe('PackageSourcePicker', () => {
  it('should_report_directory_input_when_folder_selected', () => {
    const handleSelect = vi.fn<(input: SkillUploadInput) => void>()
    render(<PackageSourcePicker onSelect={handleSelect} />)

    const file = createFile('SKILL.md')
    fireEvent.change(screen.getByLabelText('选择文件夹'), { target: { files: [file] } })

    expect(handleSelect).toHaveBeenCalledTimes(1)
    const input = handleSelect.mock.calls[0]?.[0]
    expect(input?.kind).toBe('directory')
  })

  it('should_report_zip_input_when_zip_file_picked_via_click', () => {
    const handleSelect = vi.fn<(input: SkillUploadInput) => void>()
    render(<PackageSourcePicker onSelect={handleSelect} />)

    const file = createFile('demo-skill.zip')
    fireEvent.change(screen.getByLabelText('拖入 zip,或点击选择'), { target: { files: [file] } })

    expect(handleSelect).toHaveBeenCalledWith({ kind: 'zip', file })
  })

  it('should_report_zip_input_when_zip_file_dropped', () => {
    const handleSelect = vi.fn<(input: SkillUploadInput) => void>()
    render(<PackageSourcePicker onSelect={handleSelect} />)

    const file = createFile('demo-skill.zip')
    fireEvent.drop(screen.getByText('拖入 zip,或点击选择'), {
      dataTransfer: { files: [file] },
    })

    expect(handleSelect).toHaveBeenCalledWith({ kind: 'zip', file })
  })

  it('should_report_singleSkillMd_input_when_md_file_selected', () => {
    const handleSelect = vi.fn<(input: SkillUploadInput) => void>()
    render(<PackageSourcePicker onSelect={handleSelect} />)

    const file = createFile('SKILL.md')
    fireEvent.change(screen.getByLabelText('选择单个 SKILL.md'), { target: { files: [file] } })

    expect(handleSelect).toHaveBeenCalledWith({ kind: 'singleSkillMd', file })
  })

  it('should_not_report_when_disabled_and_drop_happens', () => {
    const handleSelect = vi.fn<(input: SkillUploadInput) => void>()
    render(<PackageSourcePicker onSelect={handleSelect} disabled />)

    const file = createFile('demo-skill.zip')
    fireEvent.drop(screen.getByText('拖入 zip,或点击选择'), {
      dataTransfer: { files: [file] },
    })

    expect(handleSelect).not.toHaveBeenCalled()
  })
})
