<template>
  <div class="law-login">
    <section class="overview-panel">
      <div class="overview-content">
        <header class="brand">
          <img src="../assets/images/law-firm-logo.svg" :alt="title" class="brand-logo">
          <div>
            <strong>{{ title }}</strong>
            <span>Law Firm Management System</span>
          </div>
        </header>

        <div class="overview-heading">
          <h1>一体化管理 · 赋能律所高效运营</h1>
          <p>覆盖线索、客户、合同、案件、财务全流程，助力律所精细化管理与业务增长</p>
        </div>

        <div class="service-grid">
          <article v-for="item in serviceCards" :key="item.title" class="service-card">
            <svg-icon :icon-class="item.icon" />
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </article>
        </div>

        <section class="dashboard-card">
          <div class="dashboard-header">
            <strong>运营概览</strong>
            <div class="dashboard-filters">
              <span>本月⌄</span>
              <span>全部部门⌄</span>
            </div>
          </div>

          <div class="metric-grid">
            <article v-for="item in metrics" :key="item.label" class="metric-card">
              <div class="metric-icon" :class="item.color">
                <svg-icon :icon-class="item.icon" />
              </div>
              <div>
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <small>较上月 <em>↑ {{ item.rate }}</em></small>
              </div>
            </article>
          </div>

          <div class="dashboard-detail">
            <article class="chart-card">
              <strong>案件进度分布</strong>
              <div class="chart-body">
                <div class="donut">
                  <div>
                    <b>632</b>
                    <span>案件总数</span>
                  </div>
                </div>
                <ul>
                  <li v-for="item in caseProgress" :key="item.label">
                    <i :style="{ background: item.color }" />
                    <span>{{ item.label }}</span>
                    <b>{{ item.value }}</b>
                  </li>
                </ul>
              </div>
            </article>

            <article class="todo-card">
              <strong>待办事项</strong>
              <div v-for="item in todos" :key="item.label" class="todo-row">
                <span>{{ item.label }}</span>
                <b>{{ item.value }}</b>
              </div>
            </article>

            <article class="activity-card">
              <strong>最近动态</strong>
              <div v-for="item in activities" :key="item.name" class="activity-row">
                <span class="activity-avatar">{{ item.name.slice(0, 1) }}</span>
                <div>
                  <b>{{ item.name }}</b>
                  <p>{{ item.content }}</p>
                  <small>{{ item.time }}</small>
                </div>
              </div>
            </article>
          </div>
        </section>

        <div class="overview-slogan">
          <svg-icon icon-class="lock" />
          <span>数据驱动决策 · 流程提升效率 · 专业创造价值</span>
        </div>
      </div>
    </section>

    <main class="login-panel">
      <div class="login-content">
        <div class="login-heading">
          <h2>欢迎登录</h2>
          <p>{{ title }}</p>
        </div>

        <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form">
          <el-form-item prop="username" label="账号">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="请输入账号/手机号/邮箱"
            >
              <svg-icon slot="prefix" icon-class="user" class="input-icon" />
            </el-input>
          </el-form-item>

          <el-form-item prop="password" label="密码">
            <el-input
              v-model="loginForm.password"
              :type="passwordVisible ? 'text' : 'password'"
              auto-complete="off"
              placeholder="请输入密码"
              @keyup.enter.native="handleLogin"
            >
              <svg-icon slot="prefix" icon-class="lock" class="input-icon" />
              <svg-icon
                slot="suffix"
                :icon-class="passwordVisible ? 'eye-open' : 'eye'"
                class="password-eye"
                @click="passwordVisible = !passwordVisible"
              />
            </el-input>
          </el-form-item>

          <el-form-item v-if="captchaEnabled" prop="code" label="验证码">
            <div class="captcha-row">
              <el-input
                v-model="loginForm.code"
                auto-complete="off"
                placeholder="请输入验证码"
                @keyup.enter.native="handleLogin"
              >
                <svg-icon slot="prefix" icon-class="validCode" class="input-icon" />
              </el-input>
              <img :src="codeUrl" alt="验证码" class="captcha-image" @click="getCode">
            </div>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
          </div>

          <el-button
            :loading="loading"
            type="primary"
            class="login-button"
            @click.native.prevent="handleLogin"
          >
            <span v-if="!loading">登录系统</span>
            <span v-else>登录中...</span>
          </el-button>

          <div v-if="register" class="register-link">
            <router-link class="link-type" :to="'/register'">立即注册</router-link>
          </div>
        </el-form>

        <section class="role-card">
          <div class="role-icon"><svg-icon icon-class="peoples" /></div>
          <div>
            <strong>系统适用于多种角色</strong>
            <p>销售、案管员、律师、财务、管理员</p>
          </div>
        </section>

        <section class="security-card">
          <div v-for="item in securityItems" :key="item.title" class="security-item">
            <svg-icon :icon-class="item.icon" />
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.description }}</span>
            </div>
          </div>
        </section>
      </div>

      <footer>© 2026 {{ title }} 保留所有权利</footer>
    </main>
  </div>
</template>

<script>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from '@/utils/jsencrypt'

export default {
  name: "Login",
  data() {
    return {
      title: process.env.VUE_APP_TITLE,
      codeUrl: "",
      passwordVisible: false,
      serviceCards: [
        { title: "线索管理", description: "多渠道线索整合", icon: "user" },
        { title: "客户管理", description: "客户全生命周期", icon: "peoples" },
        { title: "合同管理", description: "起草审批与归档", icon: "documentation" },
        { title: "案件管理", description: "流程协同与跟踪", icon: "clipboard" },
        { title: "财务管理", description: "收支统计与分析", icon: "chart" }
      ],
      metrics: [
        { label: "线索总数", value: "1,289", rate: "18.6%", icon: "user", color: "blue" },
        { label: "客户总数", value: "856", rate: "12.3%", icon: "peoples", color: "cyan" },
        { label: "案件总数", value: "632", rate: "9.7%", icon: "form", color: "orange" },
        { label: "合同总额", value: "¥ 5,680万", rate: "15.8%", icon: "documentation", color: "violet" }
      ],
      caseProgress: [
        { label: "立案受理", value: "156 (24.7%)", color: "#387dff" },
        { label: "调查取证", value: "182 (28.8%)", color: "#6d74ff" },
        { label: "审理中", value: "210 (33.2%)", color: "#39b5ff" },
        { label: "已结案", value: "84 (13.3%)", color: "#50d7e2" }
      ],
      todos: [
        { label: "待跟进线索", value: "18" },
        { label: "待审批合同", value: "7" },
        { label: "临期案件", value: "12" },
        { label: "待收款项", value: "23" }
      ],
      activities: [
        { name: "张三", content: "更新了案件「2024沪0101民初123号」的进度", time: "10 分钟前" },
        { name: "李四", content: "创建了新线索「某科技公司法律咨询」", time: "35 分钟前" },
        { name: "王五", content: "审核通过了合同「法律服务协议」", time: "1 小时前" }
      ],
      securityItems: [
        { title: "本地化部署", description: "数据安全可控", icon: "lock" },
        { title: "权限隔离", description: "精细角色权限", icon: "system" },
        { title: "操作留痕", description: "全程可追溯", icon: "log" }
      ],
      loginForm: {
        username: "admin",
        password: "admin123",
        rememberMe: false,
        code: "",
        uuid: ""
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" }
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }]
      },
      loading: false,
      captchaEnabled: true,
      register: false,
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  created() {
    this.getCode()
    this.getCookie()
  },
  methods: {
    getCode() {
      getCodeImg().then(res => {
        this.captchaEnabled = res.captchaEnabled === undefined ? true : res.captchaEnabled
        if (this.captchaEnabled) {
          this.codeUrl = "data:image/gif;base64," + res.img
          this.loginForm.uuid = res.uuid
        }
      })
    },
    getCookie() {
      const username = Cookies.get("username")
      const password = Cookies.get("password")
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe),
        code: this.loginForm.code,
        uuid: this.loginForm.uuid
      }
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 })
            Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 })
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 })
          } else {
            Cookies.remove("username")
            Cookies.remove("password")
            Cookies.remove('rememberMe')
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{})
          }).catch(() => {
            this.loading = false
            if (this.captchaEnabled) {
              this.getCode()
            }
          })
        }
      })
    }
  }
}
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
$ink: #102144;
$muted: #71809a;
$blue: #1c4cff;
$cyan: #169bff;

.law-login {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: #fff;
  color: $ink;
}

.overview-panel {
  position: relative;
  width: 62%;
  height: 100vh;
  overflow: hidden;
  color: #fff;
  background:
    linear-gradient(135deg, rgba(0, 66, 180, .94), rgba(0, 142, 232, .68) 48%, rgba(80, 61, 255, .78)),
    url("../assets/images/law-office-background.jpg") center / cover;

  &::before,
  &::after {
    position: absolute;
    content: "";
    border-radius: 50%;
    filter: blur(10px);
    pointer-events: none;
  }

  &::before {
    right: -15%;
    bottom: 1%;
    width: 58%;
    height: 28%;
    background: rgba(95, 225, 255, .5);
  }

  &::after {
    left: -10%;
    bottom: -12%;
    width: 56%;
    height: 26%;
    background: rgba(70, 48, 255, .7);
  }
}

.overview-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: min(860px, calc(100% - 76px));
  height: 100vh;
  margin: 0 auto;
  padding: 36px 0 32px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;

  .brand-logo {
    width: 52px;
    height: 52px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 23px;
    letter-spacing: 1px;
  }

  span {
    margin-top: 3px;
    color: rgba(255, 255, 255, .86);
    font-size: 13px;
    letter-spacing: .5px;
  }
}

.overview-heading {
  margin-top: 54px;

  h1 {
    margin: 0;
    font-size: clamp(28px, 2.4vw, 42px);
    letter-spacing: 2px;
  }

  p {
    margin: 12px 0 0;
    color: rgba(255, 255, 255, .88);
    font-size: 17px;
    letter-spacing: .6px;
  }
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-top: 32px;
}

.service-card,
.dashboard-card {
  border: 1px solid rgba(255, 255, 255, .52);
  background: linear-gradient(145deg, rgba(255, 255, 255, .16), rgba(255, 255, 255, .08));
  box-shadow: inset 0 1px rgba(255, 255, 255, .18), 0 12px 28px rgba(5, 35, 126, .12);
  backdrop-filter: blur(12px);
}

.service-card {
  min-width: 0;
  padding: 20px 8px 18px;
  border-radius: 17px;
  text-align: center;

  .svg-icon {
    display: block;
    width: 38px;
    height: 38px;
    margin: 0 auto 12px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 16px;
  }

  span {
    margin-top: 6px;
    color: rgba(255, 255, 255, .78);
    font-size: 12px;
  }
}

.dashboard-card {
  margin-top: 16px;
  padding: 19px 22px 21px;
  border-radius: 19px;
}

.dashboard-header,
.dashboard-filters,
.metric-card,
.chart-body,
.todo-row,
.activity-row {
  display: flex;
  align-items: center;
}

.dashboard-header {
  justify-content: space-between;
  margin-bottom: 12px;
}

.dashboard-filters {
  gap: 10px;

  span {
    padding: 7px 13px;
    border: 1px solid rgba(255, 255, 255, .18);
    border-radius: 5px;
    background: rgba(255, 255, 255, .14);
    color: rgba(255, 255, 255, .9);
    font-size: 12px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.metric-card {
  gap: 11px;
  padding: 13px 10px;
  border: 1px solid rgba(255, 255, 255, .12);
  border-radius: 7px;
  background: rgba(255, 255, 255, .19);

  span,
  strong,
  small {
    display: block;
  }

  span,
  small {
    color: rgba(255, 255, 255, .74);
    font-size: 10px;
  }

  strong {
    margin: 2px 0;
    font-size: 20px;
    white-space: nowrap;
  }

  em {
    color: #81f2dc;
    font-style: normal;
  }
}

.metric-icon {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 37px;
  height: 37px;
  border-radius: 9px;
  box-shadow: 0 4px 10px rgba(13, 44, 150, .2);

  &.blue { background: linear-gradient(135deg, #2878ff, #4ab8ff); }
  &.cyan { background: linear-gradient(135deg, #16a1ff, #40daf0); }
  &.orange { background: linear-gradient(135deg, #ff9e3e, #ffca66); }
  &.violet { background: linear-gradient(135deg, #6068ff, #9a8bff); }

  .svg-icon {
    width: 21px;
    height: 21px;
  }
}

.dashboard-detail {
  display: grid;
  grid-template-columns: 1.4fr .9fr 1.08fr;
  gap: 9px;
  margin-top: 10px;
}

.chart-card,
.todo-card,
.activity-card {
  min-width: 0;
  padding: 13px;
  border: 1px solid rgba(255, 255, 255, .1);
  border-radius: 7px;
  background: rgba(255, 255, 255, .2);
  font-size: 12px;
}

.chart-body {
  gap: 14px;
  margin-top: 13px;
}

.donut {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  background: conic-gradient(#387dff 0 25%, #6d74ff 25% 54%, #39b5ff 54% 87%, #50d7e2 87%);

  &::before {
    position: absolute;
    width: 81px;
    height: 81px;
    border-radius: 50%;
    background: rgba(198, 220, 255, .88);
    content: "";
  }

  div {
    position: relative;
    color: #17356e;
    text-align: center;
  }

  b,
  span {
    display: block;
  }

  b { font-size: 21px; }
  span { margin-top: 4px; font-size: 10px; }
}

.chart-card ul {
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 7px;
    margin: 10px 0;
    color: rgba(255, 255, 255, .83);
    font-size: 10px;
    white-space: nowrap;
  }

  i {
    width: 7px;
    height: 7px;
    border-radius: 50%;
  }

  b {
    margin-left: auto;
    font-size: 10px;
    font-weight: 500;
  }
}

.todo-row {
  justify-content: space-between;
  margin-top: 9px;
  padding: 8px 8px;
  border-radius: 5px;
  background: rgba(255, 255, 255, .3);
  color: rgba(255, 255, 255, .94);
  font-size: 10px;

  b {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #ff5e75;
    font-size: 9px;
  }
}

.activity-row {
  align-items: flex-start;
  gap: 6px;
  margin-top: 9px;
  padding: 6px;
  border-radius: 5px;
  background: rgba(255, 255, 255, .25);

  .activity-avatar {
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    justify-content: center;
    width: 19px;
    height: 19px;
    border-radius: 50%;
    background: #275cbb;
    font-size: 9px;
  }

  p {
    overflow: hidden;
    margin: 2px 0;
    color: rgba(255, 255, 255, .86);
    font-size: 9px;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: rgba(255, 255, 255, .64);
    font-size: 9px;
  }
}

.overview-slogan {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: auto;
  padding-top: 24px;
  font-size: 16px;
  letter-spacing: 1px;

  .svg-icon {
    width: 23px;
    height: 23px;
  }
}

.login-panel {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 430px;
  height: 100vh;
  overflow: hidden;
  padding: 142px 32px 48px;
  background: linear-gradient(145deg, #fff, #fbfdff);
}

.login-content {
  width: min(474px, 100%);
}

.login-heading {
  padding: 0 14px;

  h2 {
    margin: 0;
    color: #102144;
    font-size: 36px;
    letter-spacing: 1px;
  }

  p {
    margin: 15px 0 35px;
    color: #24385c;
    font-size: 20px;
  }
}

.login-form,
.role-card,
.security-card {
  border: 1px solid #e2e8f2;
  border-radius: 16px;
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 14px 24px rgba(21, 43, 84, .1);
}

.login-form {
  padding: 27px 29px 26px;

  ::v-deep .el-form-item {
    margin-bottom: 20px;
  }

  ::v-deep .el-form-item__label {
    float: none;
    padding: 0 0 8px;
    color: #263858;
    font-weight: 600;
    line-height: 20px;
  }

  ::v-deep .el-form-item__content {
    line-height: 54px;
  }

  ::v-deep .el-input__inner {
    height: 54px;
    padding-right: 44px;
    padding-left: 48px;
    border-color: #d6deea;
    border-radius: 6px;
    color: #263858;
    font-size: 14px;

    &:focus {
      border-color: #2f6cff;
      box-shadow: 0 0 0 3px rgba(47, 108, 255, .08);
    }
  }

  ::v-deep .el-input__prefix,
  ::v-deep .el-input__suffix {
    display: flex;
    align-items: center;
  }

  .input-icon {
    margin-left: 8px;
    color: #5c6f95;
    font-size: 18px;
  }

  .password-eye {
    margin-right: 8px;
    color: #5c6f95;
    cursor: pointer;
    font-size: 18px;
  }
}

.captcha-row {
  display: flex;
  gap: 11px;

  .el-input {
    flex: 1;
    width: 0;
    min-width: 0;
  }
}

.captcha-image {
  width: 126px;
  height: 54px;
  border: 1px solid #d6deea;
  border-radius: 6px;
  cursor: pointer;
  object-fit: cover;
}

.form-options {
  margin: -1px 0 20px;

  ::v-deep .el-checkbox__label {
    color: #344866;
    font-size: 14px;
  }

  ::v-deep .el-checkbox__input.is-checked .el-checkbox__inner {
    border-color: #245dff;
    background: #245dff;
  }
}

.login-button {
  width: 100%;
  height: 56px;
  border: 0;
  border-radius: 7px;
  background: linear-gradient(100deg, #203dff, #139cff);
  box-shadow: 0 9px 18px rgba(36, 93, 255, .18);
  font-size: 17px;
  font-weight: 600;

  &:hover,
  &:focus {
    background: linear-gradient(100deg, #1733ed, #078ce9);
  }
}

.register-link {
  margin-top: 14px;
  text-align: right;
}

.role-card,
.security-card {
  display: flex;
  box-shadow: none;
}

.role-card {
  align-items: center;
  gap: 17px;
  margin-top: 32px;
  padding: 14px 24px;

  .role-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 54px;
    height: 54px;
    border-radius: 50%;
    background: #edf5ff;
    color: #2667ff;
    font-size: 31px;
  }

  p {
    margin: 7px 0 0;
    color: #6d7d96;
    font-size: 13px;
  }
}

.security-card {
  justify-content: space-around;
  gap: 12px;
  margin-top: 20px;
  padding: 17px 19px;
}

.security-item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;

  > .svg-icon {
    flex: 0 0 auto;
    color: #1760ff;
    font-size: 26px;
  }

  strong,
  span {
    display: block;
    white-space: nowrap;
  }

  strong {
    color: #263858;
    font-size: 13px;
  }

  span {
    margin-top: 5px;
    color: #8290a5;
    font-size: 11px;
  }
}

footer {
  position: absolute;
  bottom: 27px;
  color: #9aa7b9;
  font-size: 12px;
}

@media (max-width: 1380px), (max-height: 900px) {
  .overview-content {
    width: min(780px, calc(100% - 52px));
    padding-top: 25px;
  }

  .overview-heading {
    margin-top: 32px;
  }

  .overview-heading p {
    font-size: 14px;
  }

  .service-grid {
    margin-top: 23px;
  }

  .service-card {
    padding: 14px 6px;
  }

  .service-card .svg-icon {
    width: 30px;
    height: 30px;
    margin-bottom: 9px;
  }

  .dashboard-card {
    padding: 13px 15px 14px;
  }

  .metric-card {
    padding: 9px 7px;
  }

  .metric-card strong {
    font-size: 16px;
  }

  .donut {
    width: 102px;
    height: 102px;
  }

  .donut::before {
    width: 65px;
    height: 65px;
  }

  .chart-card,
  .todo-card,
  .activity-card {
    padding: 9px;
  }

  .todo-row,
  .activity-row {
    margin-top: 6px;
    padding: 5px;
  }

  .login-panel {
    padding-top: 109px;
  }

  .login-heading h2 {
    font-size: 32px;
  }

  .login-heading p {
    margin: 10px 0 17px;
    font-size: 17px;
  }

  .login-form {
    padding: 15px 22px 16px;
    border-radius: 13px;
  }

  .login-form ::v-deep .el-form-item {
    margin-bottom: 12px;
  }

  .login-form ::v-deep .el-form-item__label {
    padding-bottom: 4px;
  }

  .login-form ::v-deep .el-form-item__content {
    line-height: 46px;
  }

  .login-form ::v-deep .el-input__inner,
  .captcha-image {
    height: 46px;
  }

  .form-options {
    margin: 0 0 13px;
  }

  .login-button {
    height: 48px;
    font-size: 16px;
  }

  .role-card {
    gap: 13px;
    margin-top: 14px;
    padding: 10px 18px;
  }

  .role-card .role-icon {
    width: 45px;
    height: 45px;
    font-size: 26px;
  }

  .role-card p {
    margin-top: 4px;
  }

  .security-card {
    margin-top: 12px;
    padding: 12px 15px;
  }

  footer {
    bottom: 16px;
  }
}

@media (max-width: 1100px) {
  .overview-panel {
    width: 58%;
  }

  .service-card span,
  .dashboard-detail {
    display: none;
  }

  .overview-slogan {
    margin-top: 25px;
  }
}

@media (max-width: 900px) {
  .overview-panel {
    display: none;
  }

  .login-panel {
    height: auto;
    min-height: 100vh;
    overflow: visible;
    min-width: 0;
    padding: 48px 22px 66px;
    background:
      radial-gradient(circle at 7% 8%, rgba(36, 108, 255, .11), transparent 34%),
      linear-gradient(145deg, #fff, #f7fbff);
  }
}

@media (max-width: 520px) {
  .login-heading {
    padding: 0 3px;
  }

  .login-heading h2 {
    font-size: 31px;
  }

  .login-heading p {
    font-size: 17px;
  }

  .login-form {
    padding-right: 20px;
    padding-left: 20px;
  }

  .captcha-image {
    width: 106px;
  }

  .security-card {
    display: grid;
    grid-template-columns: 1fr 1fr;
    justify-content: start;
  }

  footer {
    bottom: 21px;
  }
}
</style>
