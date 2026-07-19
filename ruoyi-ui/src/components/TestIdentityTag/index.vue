<template>
  <el-tag v-if="userType === '99'" type="warning" size="mini">测试身份</el-tag>
</template>

<script>
export default {
  name: 'TestIdentityTag',
  props: {
    userType: {
      type: String,
      default: ''
    }
  }
}
</script>
