-- Align persisted account status with runtime lifecycle policy.
-- Any future-dated USER account is a PRE onboarding account.

UPDATE users
SET account_status = 'PRE'
WHERE role = 'USER'
  AND hire_date > CURRENT_DATE
  AND account_status <> 'PRE';
