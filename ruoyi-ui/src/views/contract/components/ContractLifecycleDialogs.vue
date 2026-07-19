<template>
  <div>
    <el-dialog title="合同审批" :visible="approvalOpen" @close="$emit('update:approvalOpen', false)" width="520px" :custom-class="dialogClass" append-to-body>
      <el-form ref="approval" :model="approvalForm" :rules="approvalRules" label-width="90px">
        <el-form-item label="审批动作" prop="action">
          <el-radio-group v-model="approvalForm.action" class="approval-action-group">
            <el-radio-button v-for="item in approvalActionOptions" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见" prop="opinion">
          <el-input v-model="approvalForm.opinion" :size="controlSize" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button :size="controlSize" @click="$emit('update:approvalOpen', false)">取消</el-button>
        <el-button :size="controlSize" type="primary" @click="saveApproval">提交</el-button>
      </div>
    </el-dialog>

    <el-dialog title="合同签署" :visible="signOpen" @close="$emit('update:signOpen', false)" width="460px" :custom-class="dialogClass" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="签署动作">
          <el-radio-group v-model="signForm.signStatus" class="lifecycle-action-group">
            <el-radio-button v-for="item in signActionOptions" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert :closable="false" type="info" show-icon :title="signActionTip" />
      </el-form>
      <div slot="footer">
        <el-button :size="controlSize" @click="$emit('update:signOpen', false)">取消</el-button>
        <el-button :size="controlSize" type="primary" :disabled="!signForm.signStatus" @click="saveSign">确定</el-button>
      </div>
    </el-dialog>

    <el-dialog title="确认开票" :visible="invoiceOpen" @close="$emit('update:invoiceOpen', false)" width="460px" :custom-class="dialogClass" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="开票动作">
          <el-radio-group v-model="invoiceForm.invoiceStatus" class="lifecycle-action-group">
            <el-radio-button v-for="item in invoiceActionOptions" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert :closable="false" type="info" show-icon :title="invoiceActionTip" />
      </el-form>
      <div slot="footer">
        <el-button :size="controlSize" @click="$emit('update:invoiceOpen', false)">取消</el-button>
        <el-button :size="controlSize" type="primary" :disabled="!invoiceForm.invoiceStatus" @click="saveInvoice">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ContractLifecycleDialogs',
  props: {
    approvalOpen: Boolean, signOpen: Boolean, invoiceOpen: Boolean,
    approvalForm: Object, approvalRules: Object, approvalActionOptions: Array,
    signForm: Object, signActionOptions: Array, invoiceForm: Object, invoiceActionOptions: Array,
    signActionTip: String, invoiceActionTip: String,
    dictOptions: Object, controlSize: String, dialogClass: String,
    saveApproval: Function, saveSign: Function, saveInvoice: Function
  },
  methods: {
    validateApproval(callback) { if (this.$refs.approval) this.$refs.approval.validate(callback) },
    clearApproval() { if (this.$refs.approval) this.$refs.approval.clearValidate() }
  }
}
</script>
