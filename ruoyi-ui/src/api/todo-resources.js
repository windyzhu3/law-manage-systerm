import request from '@/utils/request'

export function listEventResources(params) { return request({ url: '/todo/config/resources/events', method: 'get', params }) }
export function getEventResource(id) { return request({ url: `/todo/config/resources/events/${id}`, method: 'get' }) }
export function createEventResource(data) { return request({ url: '/todo/config/resources/events', method: 'post', data }) }
export function updateEventResource(id, data) { return request({ url: `/todo/config/resources/events/${id}`, method: 'put', data }) }
export function createEventResourceVersion(id, data) { return request({ url: `/todo/config/resources/events/${id}/versions`, method: 'post', data }) }
export function changeEventResourceStatus(id, data) { return request({ url: `/todo/config/resources/events/${id}/status`, method: 'post', data }) }

export function listValidatorResources(params) { return request({ url: '/todo/config/resources/validators', method: 'get', params }) }
export function listFieldResources(params) { return request({ url: '/todo/config/resources/fields', method: 'get', params }) }
export function listMaterialResources(params) { return request({ url: '/todo/config/resources/materials', method: 'get', params }) }
export function listDodRecipeResources(params) { return request({ url: '/todo/config/resources/dod-recipes', method: 'get', params }) }
export function createConfigurationResource(data) { return request({ url: '/todo/config/resources/items', method: 'post', data }) }
export function updateConfigurationResource(id, data) { return request({ url: `/todo/config/resources/items/${id}`, method: 'put', data }) }
export function listConfigurationDataSources() { return request({ url: '/todo/config/resources/data-sources', method: 'get' }) }
