import type { Project } from '../api/client'

export function projectName(project?: Project): string {
  return (
    (project?.spec?.autoDetectName !== false && project?.status?.haloDisplayName) ||
    project?.spec?.displayName ||
    project?.metadata.name ||
    ''
  )
}
