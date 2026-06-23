package com.ruoyi.web.controller.customer;

import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/customer")
public class BizCustomerController extends BaseController
{
    @Autowired
    private IBizCustomerService customerService;

    @Autowired
    private ISysUserService userService;

    @PreAuthorize("@ss.hasAnyPermi('customer:add,customer:edit,customer:assign')")
    @GetMapping("/owner/options")
    public AjaxResult ownerOptions()
    {
        return success(userService.selectUserList(new SysUser()));
    }

    @PreAuthorize("@ss.hasAnyPermi('customer:list,customer:query')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        return success(customerService.selectDashboard());
    }

    @PreAuthorize("@ss.hasAnyPermi('customer:list,customer:query')")
    @GetMapping("/list")
    public TableDataInfo list(BizCustomer customer)
    {
        startPage();
        return getDataTable(customerService.selectCustomerList(customer));
    }

    @PreAuthorize("@ss.hasPermi('customer:query')")
    @GetMapping("/{customerId}")
    public AjaxResult getInfo(@PathVariable Long customerId)
    {
        return success(customerService.selectCustomerById(customerId));
    }

    @Log(title = "customer", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('customer:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, BizCustomer customer)
    {
        ExcelUtil<BizCustomer> util = new ExcelUtil<>(BizCustomer.class);
        util.exportExcel(response, customerService.selectCustomerList(customer), "customer");
    }

    @Log(title = "customer", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('customer:import')")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelUtil<BizCustomer> util = new ExcelUtil<>(BizCustomer.class);
        List<BizCustomer> customerList = util.importExcel(file.getInputStream());
        return success(customerService.importCustomer(customerList, updateSupport, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('customer:import')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<BizCustomer> util = new ExcelUtil<>(BizCustomer.class);
        util.importTemplateExcel(response, "customer");
    }

    @Log(title = "customer", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('customer:add')")
    @PostMapping
    public AjaxResult add(@RequestBody BizCustomer customer)
    {
        return toAjax(customerService.insertCustomer(customer));
    }

    @Log(title = "customer", businessType = BusinessType.UPDATE)
    @PreAuthorize("@ss.hasPermi('customer:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody BizCustomer customer)
    {
        return toAjax(customerService.updateCustomer(customer));
    }

    @Log(title = "customer", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('customer:remove')")
    @DeleteMapping("/{customerIds}")
    public AjaxResult remove(@PathVariable Long[] customerIds)
    {
        return toAjax(customerService.deleteCustomerByIds(customerIds));
    }

    @PreAuthorize("@ss.hasPermi('customer:contact:list')")
    @GetMapping("/contact/list")
    public TableDataInfo contactList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(customerService.selectContacts(params));
    }

    @PreAuthorize("@ss.hasPermi('customer:contact:add')")
    @Log(title = "customer-contact", businessType = BusinessType.INSERT)
    @PostMapping("/contact")
    public AjaxResult addContact(@RequestBody Map<String, Object> contact)
    {
        return toAjax(customerService.insertContact(contact));
    }

    @PreAuthorize("@ss.hasPermi('customer:contact:edit')")
    @Log(title = "customer-contact", businessType = BusinessType.UPDATE)
    @PutMapping("/contact")
    public AjaxResult editContact(@RequestBody Map<String, Object> contact)
    {
        return toAjax(customerService.updateContact(contact));
    }

    @PreAuthorize("@ss.hasPermi('customer:contact:remove')")
    @Log(title = "customer-contact", businessType = BusinessType.DELETE)
    @DeleteMapping("/contact/{contactId}")
    public AjaxResult removeContact(@PathVariable Long contactId)
    {
        return toAjax(customerService.deleteContact(contactId));
    }

    @PreAuthorize("@ss.hasPermi('customer:followup:list')")
    @GetMapping("/followup/list")
    public TableDataInfo followupList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(customerService.selectFollowups(params));
    }

    @PreAuthorize("@ss.hasPermi('customer:followup:add')")
    @Log(title = "customer-followup", businessType = BusinessType.INSERT)
    @PostMapping("/followup")
    public AjaxResult addFollowup(@RequestBody Map<String, Object> followup)
    {
        return toAjax(customerService.insertFollowup(followup));
    }

    @PreAuthorize("@ss.hasPermi('customer:followup:remove')")
    @Log(title = "customer-followup", businessType = BusinessType.DELETE)
    @DeleteMapping("/followup/{followupId}")
    public AjaxResult removeFollowup(@PathVariable Long followupId)
    {
        return toAjax(customerService.deleteFollowup(followupId));
    }

    @PreAuthorize("@ss.hasPermi('customer:tag:list')")
    @GetMapping("/tag/list")
    public AjaxResult tagList(@RequestParam Map<String, Object> params)
    {
        return success(customerService.selectTags(params));
    }

    @PreAuthorize("@ss.hasPermi('customer:tag:add')")
    @Log(title = "customer-tag", businessType = BusinessType.INSERT)
    @PostMapping("/tag")
    public AjaxResult addTag(@RequestBody Map<String, Object> tag)
    {
        return toAjax(customerService.insertTag(tag));
    }

    @PreAuthorize("@ss.hasPermi('customer:tag:edit')")
    @Log(title = "customer-tag", businessType = BusinessType.UPDATE)
    @PutMapping("/tag")
    public AjaxResult editTag(@RequestBody Map<String, Object> tag)
    {
        return toAjax(customerService.updateTag(tag));
    }

    @PreAuthorize("@ss.hasPermi('customer:tag:remove')")
    @Log(title = "customer-tag", businessType = BusinessType.DELETE)
    @DeleteMapping("/tag/{tagId}")
    public AjaxResult removeTag(@PathVariable Long tagId)
    {
        return toAjax(customerService.deleteTag(tagId));
    }

    @PreAuthorize("@ss.hasPermi('customer:tag:assign')")
    @GetMapping("/{customerId}/tags")
    public AjaxResult getTags(@PathVariable Long customerId)
    {
        return success(customerService.selectCustomerTagIds(customerId));
    }

    @PreAuthorize("@ss.hasPermi('customer:tag:assign')")
    @Log(title = "customer-tags", businessType = BusinessType.UPDATE)
    @PostMapping("/{customerId}/tags")
    public AjaxResult setTags(@PathVariable Long customerId, @RequestBody Long[] tagIds)
    {
        return toAjax(customerService.setCustomerTags(customerId, tagIds));
    }

    @PreAuthorize("@ss.hasPermi('customer:merge:list')")
    @GetMapping("/merge/candidates")
    public AjaxResult mergeCandidates(BizCustomer customer)
    {
        return success(customerService.selectMergeCandidates(customer));
    }

    @PreAuthorize("@ss.hasPermi('customer:merge:list')")
    @GetMapping("/merge/logs")
    public TableDataInfo mergeLogs(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(customerService.selectMergeLogs(params));
    }

    @PreAuthorize("@ss.hasPermi('customer:merge:merge')")
    @Log(title = "customer-merge", businessType = BusinessType.UPDATE)
    @PostMapping("/merge")
    public AjaxResult merge(@RequestBody Map<String, Object> body)
    {
        Long mainCustomerId = requiredLong(body, "mainCustomerId", "请选择主客户");
        Long mergedCustomerId = requiredLong(body, "mergedCustomerId", "请选择待合并客户");
        String content = text(body, "content");
        return toAjax(customerService.mergeCustomer(mainCustomerId, mergedCustomerId, content));
    }

    private Long requiredLong(Map<String, Object> body, String key, String message)
    {
        Object value = body == null ? null : body.get(key);
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
        {
            throw new ServiceException(message);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String text(Map<String, Object> body, String key)
    {
        Object value = body == null ? null : body.get(key);
        return value == null || "null".equalsIgnoreCase(String.valueOf(value)) ? null : String.valueOf(value);
    }
}
