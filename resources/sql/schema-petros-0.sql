
CREATE CACHED TABLE user (
  user_id INTEGER IDENTITY,
  email_addr VARCHAR(255) UNIQUE,
  password VARCHAR(255)
);

CREATE CACHED TABLE verification_link (
  verification_link_id INTEGER IDENTITY,
  link_uuid VARCHAR(36) NOT NULL UNIQUE,
  created_on TIMESTAMP NOT NULL,
  verifies_user_id INTEGER NOT NULL REFERENCES user(user_id)    
);

CREATE CACHED TABLE role (
  role_id INTEGER IDENTITY,
  role_name VARCHAR(32) NOT NULL
);

INSERT INTO role(role_name) values('verified');
INSERT INTO role(role_name) values('counter');
INSERT INTO role(role_name) values('accountant');
INSERT INTO role(role_name) values('administrator');

CREATE CACHED TABLE user_role (
  user_id INTEGER NOT NULL REFERENCES user(user_id),
  role_id INTEGER NOT NULL REFERENCES role(role_id)
);

CREATE CACHED TABLE count_sheet (
  count_sheet_id INTEGER IDENTITY,
  
  creator_user_id INTEGER REFERENCES user(user_id),
  created_on TIMESTAMP NOT NULL,

  finalizer_user_id INTEGER NULL REFERENCES user(user_id),
  final_on TIMESTAMP NULL
);

CREATE CACHED TABLE account (
  account_id INTEGER IDENTITY,
  name VARCHAR(32) NOT NULL
);

INSERT INTO account(name) values('Pledge');
INSERT INTO account(name) values('Plate Cash');
INSERT INTO account(name) values('Altar Guild');
INSERT INTO account(name) values('Capital Campaign');
INSERT INTO account(name) values('Food Pantry');
INSERT INTO account(name) values('J2A - Youth Trip');
INSERT INTO account(name) values('Memorial Fund');
INSERT INTO account(name) values('Rector''s Discretionary');
INSERT INTO account(name) values('Rector''s Rent');
INSERT INTO account(name) values('Hall Rental');
INSERT INTO account(name) values('Play and Learn Rent');
INSERT INTO account(name) values('Play and Learn Utilities');
INSERT INTO account(name) values('AA Donations');
INSERT INTO account(name) values('Fundraising');
INSERT INTO account(name) values('Medical Insurance');
INSERT INTO account(name) values('Other (see notes)');

CREATE CACHED TABLE contributor (
  contributor_id INTEGER IDENTITY,
  name VARCHAR(64) NOT NULL
);

CREATE CACHED TABLE deposit_item (
  item_id INTEGER IDENTITY,
  count_sheet_id INTEGER REFERENCES count_sheet(count_sheet_id),
  contributor_id INTEGER NULL REFERENCES contributor(contributor_id),
  account_id INTEGER REFERENCES account(account_id),
  check_number NUMERIC(10) NULL,
  amount NUMERIC(9,2) NOT NULL,
  notes VARCHAR(1024)
);
