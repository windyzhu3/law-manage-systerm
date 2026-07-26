package com.ruoyi.web.e2e;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.law.file.application.FileObjectService;
import com.law.file.application.FileObjectService.RetireFileObjectCommand;
import com.law.file.application.FileObjectService.RetireFileObjectView;
import com.law.file.domain.FileObject.FileActor;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class E2eOwnedFileCleanupServiceTest
{
    @Mock private JdbcTemplate jdbc;
    @Mock private FileObjectService files;
    @Mock private TransactionOperations transactions;

    @Test
    void temporarilyRestoresOnlyTheExactFixtureAccessAndUsesTheRealRetirementLifecycle()
        throws Exception
    {
        FileActor actor=new FileActor(101L,"ft_sales",201L);
        executeTransactions();
        ResultSet row=org.mockito.Mockito.mock(ResultSet.class);
        when(row.getLong("lead_id")).thenReturn(29L);
        when(row.getObject("owner_id")).thenReturn(null);
        when(row.getObject("dept_id")).thenReturn(null);
        when(jdbc.query(
            ArgumentMatchers.eq(E2eOwnedFileCleanupService.OWNERSHIP_SQL),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),
            ArgumentMatchers.eq(7L),ArgumentMatchers.eq(3L),
            ArgumentMatchers.eq("LEAD\\_E2E\\_abc\\_%"),
            ArgumentMatchers.eq(101L),ArgumentMatchers.eq(101L))).thenAnswer(invocation->{
                org.springframework.jdbc.core.RowMapper<?> mapper=invocation.getArgument(1);
                return List.of(mapper.mapRow(row,0));
            });
        when(jdbc.update(E2eOwnedFileCleanupService.TEMPORARY_ACCESS_SQL,101L,201L,29L))
            .thenReturn(1);
        when(jdbc.update(E2eOwnedFileCleanupService.RESTORE_ACCESS_SQL,null,null,29L))
            .thenReturn(1);

        E2eOwnedFileCleanupService service=new E2eOwnedFileCleanupService(jdbc,files,transactions);
        service.retire(7L,new E2eOwnedFileCleanupService.CleanupCommand(
            "cleanup-7-3",3L,"abc"),actor);

        verify(files).retire(7L,new RetireFileObjectCommand("cleanup-7-3",3L,true),actor);
        verify(jdbc).update(E2eOwnedFileCleanupService.RESTORE_ACCESS_SQL,null,null,29L);
    }

    @Test
    void rejectsAFileOutsideTheExactOwnedE2eLeadBoundary()
    {
        FileActor actor=new FileActor(101L,"ft_sales",201L);
        executeTransactions();
        when(jdbc.query(
            ArgumentMatchers.eq(E2eOwnedFileCleanupService.OWNERSHIP_SQL),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),
            ArgumentMatchers.eq(7L),ArgumentMatchers.eq(3L),
            ArgumentMatchers.eq("LEAD\\_E2E\\_wrong\\_%"),
            ArgumentMatchers.eq(101L),ArgumentMatchers.eq(101L))).thenReturn(List.of());

        E2eOwnedFileCleanupService service=new E2eOwnedFileCleanupService(jdbc,files,transactions);
        assertThrows(AccessDeniedException.class,()->service.retire(
            7L,new E2eOwnedFileCleanupService.CleanupCommand(
                "cleanup-7-3",3L,"wrong"),actor));

        verify(files,never()).retire(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restoresTheAssertedLeadAccessStateWhenTheRealLifecycleFails()
        throws Exception
    {
        FileActor actor=new FileActor(101L,"ft_sales",201L);
        executeTransactions();
        ResultSet row=org.mockito.Mockito.mock(ResultSet.class);
        when(row.getLong("lead_id")).thenReturn(29L);
        when(row.getObject("owner_id")).thenReturn(null);
        when(row.getObject("dept_id")).thenReturn(null);
        when(jdbc.query(
            ArgumentMatchers.eq(E2eOwnedFileCleanupService.OWNERSHIP_SQL),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),
            ArgumentMatchers.eq(7L),ArgumentMatchers.eq(3L),
            ArgumentMatchers.eq("LEAD\\_E2E\\_abc\\_%"),
            ArgumentMatchers.eq(101L),ArgumentMatchers.eq(101L))).thenAnswer(invocation->{
                org.springframework.jdbc.core.RowMapper<?> mapper=invocation.getArgument(1);
                return List.of(mapper.mapRow(row,0));
            });
        when(jdbc.update(E2eOwnedFileCleanupService.TEMPORARY_ACCESS_SQL,101L,201L,29L))
            .thenReturn(1);
        when(jdbc.update(E2eOwnedFileCleanupService.RESTORE_ACCESS_SQL,null,null,29L))
            .thenReturn(1);
        when(files.retire(7L,new RetireFileObjectCommand("cleanup-7-3",3L,true),actor))
            .thenThrow(new IllegalStateException("storage lifecycle failed"));

        E2eOwnedFileCleanupService service=new E2eOwnedFileCleanupService(jdbc,files,transactions);
        assertThrows(IllegalStateException.class,()->service.retire(7L,
            new E2eOwnedFileCleanupService.CleanupCommand("cleanup-7-3",3L,"abc"),actor));

        verify(jdbc).update(E2eOwnedFileCleanupService.RESTORE_ACCESS_SQL,null,null,29L);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void executeTransactions()
    {
        when(transactions.execute(ArgumentMatchers.<TransactionCallback<Object>>any()))
            .thenAnswer(invocation->((TransactionCallback)invocation.getArgument(0))
                .doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class)));
    }
}
