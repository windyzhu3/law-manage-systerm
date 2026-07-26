const TAG_CONFIRM_PERMISSION = 'lead:tag:confirm'
const ALL_PERMISSION = '*:*:*'

function normalizedPermissions(permissions) {
  return Array.isArray(permissions) ? permissions.filter(Boolean).map(String) : []
}

function hasTagConfirmPermission(permissions) {
  return normalizedPermissions(permissions)
    .some(permission => permission === ALL_PERMISSION || permission === TAG_CONFIRM_PERMISSION)
}

function tagPermissionRenderKey(mode, permissions) {
  const values = normalizedPermissions(permissions).sort()
  return `${mode || 'unknown'}:${values.join('|')}`
}

module.exports = {
  TAG_CONFIRM_PERMISSION,
  hasTagConfirmPermission,
  tagPermissionRenderKey
}
