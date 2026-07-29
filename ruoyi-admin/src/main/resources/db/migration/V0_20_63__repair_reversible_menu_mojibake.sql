-- Legacy bootstrap clients decoded UTF-8 menu bytes as latin1 and then stored
-- that mojibake as valid UTF-8. Repair only values whose latin1 reversal
-- produces Chinese text; intentional English labels and localized rows are
-- therefore left unchanged.
update sys_menu
set menu_name=convert(cast(convert(menu_name using latin1) as binary) using utf8mb4),
    update_by='flyway',
    update_time=sysdate()
where menu_name<>''
  and menu_name not regexp '[一-鿿]'
  and convert(cast(convert(menu_name using latin1) as binary) using utf8mb4)
      regexp '[一-鿿]';
