<template>
  <section class="lead-hero">
    <div class="hero-content">
      <span>LEAD OPERATION CENTER</span>
      <h2>统一管理线索流转与销售跟进</h2>
      <p>覆盖线索获取、分配、公海领取与持续跟进，让每一条咨询都有清晰归属。</p>
      <div class="hero-actions">
        <el-button v-hasPermi="['lead:add']" type="primary" icon="el-icon-plus" @click="$emit('create')">新增线索</el-button>
        <el-button v-hasPermi="['lead:pool:list']" icon="el-icon-user" @click="$emit('pool')">查看公海</el-button>
      </div>
    </div>
    <div class="hero-visual">
      <i class="orbit orbit-one" />
      <i class="orbit orbit-two" />
      <i class="person person-one"><svg-icon icon-class="user" /></i>
      <i class="person person-two"><svg-icon icon-class="peoples" /></i>
      <i class="person person-three"><svg-icon icon-class="user" /></i>
      <div class="funnel">
        <span />
        <span />
        <span />
      </div>
    </div>
  </section>
</template>

<script>
export default { name: 'LeadHero' }
</script>

<style lang="scss" scoped>
.lead-hero { position: relative; min-height: 168px; overflow: hidden; border-radius: 13px; background: linear-gradient(115deg,#123ee9 0%,#086ce8 55%,#8237ed 100%); box-shadow: 0 12px 24px rgba(25,74,221,.18); color: #fff; }
.lead-hero::before,.lead-hero::after { position:absolute; right:-5%; bottom:-72px; width:72%; height:160px; border-radius:50%; border-top:1px solid rgba(255,255,255,.28); background:linear-gradient(180deg,rgba(67,211,255,.17),transparent 70%); content:""; transform:rotate(-4deg); }
.lead-hero::after { right:9%; bottom:-92px; width:56%; opacity:.7; }
.hero-content { position:relative; z-index:2; padding:24px 28px; span { color:#b8d8ff; font-size:10px; font-weight:700; letter-spacing:1.6px; } h2 { margin:6px 0; font-size:27px; letter-spacing:1px; } p { margin:0; color:#d9e8ff; font-size:13px; } }
.hero-actions { display:flex; gap:10px; margin-top:18px; ::v-deep .el-button { min-width:112px; border-color:rgba(255,255,255,.34); color:#fff; background:rgba(255,255,255,.08); } ::v-deep .el-button--primary { border-color:#fff; color:#1752db; background:#fff; } }
.hero-visual { position:absolute; top:0; right:0; width:38%; height:100%; opacity:.72; }
.funnel { position:absolute; right:55px; top:28px; width:124px; height:112px; border:10px solid rgba(255,255,255,.18); border-top-width:18px; border-radius:42% 42% 48% 48%; clip-path:polygon(0 0,100% 0,66% 100%,34% 100%); span { display:block; width:62px; margin:17px auto 0; border-top:7px solid rgba(255,255,255,.38); border-radius:8px; } span:nth-child(2){width:44px}span:nth-child(3){width:26px} }
.person { position:absolute; display:flex; align-items:center; justify-content:center; width:31px; height:31px; border-radius:50%; color:#fff; background:rgba(255,255,255,.35); font-size:15px; }.person-one{top:22px;right:145px}.person-two{top:75px;right:222px}.person-three{right:24px;bottom:19px}
.orbit { position:absolute; right:5px; top:66px; width:255px; height:73px; border-radius:50%; border:1px solid rgba(255,255,255,.18); transform:rotate(8deg); }.orbit-two{right:35px;top:46px;width:210px}
@media(max-width:900px){.hero-visual{display:none}.hero-content h2{font-size:22px}}
.hero-content span{font-size:var(--lead-font-mini,10px)}.hero-content h2{font-size:var(--lead-font-hero,27px)}.hero-content p{font-size:var(--lead-font-base,13px)}
</style>
