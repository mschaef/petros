
CREATE CACHED TABLE user (
  user_id BIGINT IDENTITY,
  email_addr VARCHAR(255) UNIQUE,
  password VARCHAR(255)
);

CREATE CACHED TABLE role (
  role_id BIGINT IDENTITY,
  role_name VARCHAR(32) NOT NULL
);

INSERT INTO role(role_name) values('verified');
INSERT INTO role(role_name) values('accountant');
INSERT INTO role(role_name) values('administrator');

CREATE CACHED TABLE user_role (
  user_id BIGINT REFERENCES user(user_id),
  role_id BIGINT REFERENCES role(role_id)
);

CREATE CACHED TABLE count_sheet (
  count_sheet_id BIGINT IDENTITY,
  
  creator_user_id BIGINT REFERENCES user(user_id),
  created_on TIMESTAMP NOT NULL,

  finalizer_user_id BIGINT NULL REFERENCES user(user_id),
  final_on TIMESTAMP NULL
);

CREATE CACHED TABLE account (
  account_id BIGINT IDENTITY,
  name VARCHAR(32) NOT NULL
);

INSERT INTO account(name) values('Pledge');
INSERT INTO account(name) values('Altar Guild');
INSERT INTO account(name) values('Holiday Flowers');
INSERT INTO account(name) values('Capital Campaign');
INSERT INTO account(name) values('Food Pantry');
INSERT INTO account(name) values('J2A - Youth Trip');
INSERT INTO account(name) values('Memorial Fund');
INSERT INTO account(name) values('Rector''s Discretionary');
INSERT INTO account(name) values('Rental Income');
INSERT INTO account(name) values('Other (see notes)');

CREATE CACHED TABLE contributor (
  contributor_id BIGINT IDENTITY,
  name VARCHAR(64) NOT NULL
);

CREATE CACHED TABLE deposit_item (
  item_id BIGINT IDENTITY,
  count_sheet_id BIGINT REFERENCES count_sheet(count_sheet_id),
  contributor_id BIGINT NULL REFERENCES contributor(contributor_id),
  account_id BIGINT REFERENCES account(account_id),
  check_number NUMERIC(10) NULL,
  amount NUMERIC(9,2) NOT NULL,
  notes VARCHAR(1024)
);
