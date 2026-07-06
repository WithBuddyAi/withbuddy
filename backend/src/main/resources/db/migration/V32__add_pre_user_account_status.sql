-- account_state = PRE 추가

UPDATE users
SET account_status = 'PRE'
WHERE role = 'USER'
  AND hire_date > CURRENT_DATE
  AND hire_date <= DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY);

UPDATE users
SET account_status = 'INACTIVE'
WHERE role = 'USER'
  AND hire_date > DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY);

UPDATE users
SET account_status = 'ACTIVE'
WHERE role = 'USER'
  AND account_status = 'PRE'
  AND hire_date <= CURRENT_DATE;
