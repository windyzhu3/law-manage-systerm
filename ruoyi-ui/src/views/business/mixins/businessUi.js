export default {
  computed: {
    appSize() {
      return this.$store.getters.size || 'medium'
    },
    controlSize() {
      return this.appSize === 'default' ? undefined : this.appSize
    },
    dialogClass() {
      return 'biz-dialog biz-size-' + this.appSize
    },
    messageBoxClass() {
      return 'biz-message-box biz-size-' + this.appSize
    },
    pageTitle() {
      const meta = this.businessPageMeta || {}
      return (meta.titles && meta.titles[this.mode]) || meta.defaultTitle || ''
    },
    pageDescription() {
      const meta = this.businessPageMeta || {}
      return (meta.descriptions && meta.descriptions[this.mode]) || ''
    }
  },
  methods: {
    dictLabel(type, value) {
      return this.selectDictLabel(this.dict.type[type] || [], value) || (value || '-')
    },
    dictDefault(type) {
      const options = this.dict.type[type] || []
      const item = options.find(item => item.isDefault === 'Y') || options[0]
      return item ? item.value : undefined
    },
    dictValue(type, value) {
      const options = this.dict.type[type] || []
      const item = options.find(item => item.value === value)
      return item ? item.value : value
    },
    sameValue(left, right) {
      return String(left) === String(right)
    },
    hasValue(list, value) {
      return list.some(item => this.sameValue(item, value))
    },
    formatMoney(value) {
      return value == null ? '-' : '¥ ' + Number(value).toLocaleString()
    },
    fileNameFromUrl(value) {
      if (!value) return '-'
      const clean = String(value).split('?')[0]
      return decodeURIComponent(clean.substring(clean.lastIndexOf('/') + 1))
    },
    fileExtFromUrl(value) {
      const name = this.fileNameFromUrl(value)
      return name && name.includes('.') ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : ''
    },
    businessFileUrl(value) {
      if (!value) return ''
      if (/^https?:\/\//i.test(value)) return value
      return process.env.VUE_APP_BASE_API + value
    },
    openBusinessFile(value) {
      const url = this.businessFileUrl(value)
      if (url) window.open(url, '_blank')
    },
    maskMobile(value) {
      return value ? value.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '-'
    },
    avatar(value) {
      return value ? String(value).slice(0, 1) : '?'
    },
    messageBoxOptions(options = {}) {
      return {
        customClass: this.messageBoxClass,
        ...options
      }
    }
  }
}
