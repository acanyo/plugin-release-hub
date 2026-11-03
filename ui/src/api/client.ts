import type { AxiosInstance } from 'axios'
import { ReleaseHubAdminApi } from './generated/api/release-hub-admin-api'
import { ReleaseHubPublicApi } from './generated/api/release-hub-public-api'
export function adminClient(axios: AxiosInstance) {
  return new ReleaseHubAdminApi(undefined, '', axios)
}
export function publicClient() {
  return new ReleaseHubPublicApi(undefined, '')
}
export type {
  ProjectView,
  ReleaseView,
  ReleasePage,
  PublicSettings,
  Project,
  ReleaseProjectSpec,
  Subscription,
  Delivery,
  SyncLog,
  Stats,
  AccountView,
  RepositoryCandidate,
  RepositoryImportItem,
} from './generated/models'
