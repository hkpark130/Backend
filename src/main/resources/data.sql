INSERT INTO categories (name, img) values ('노트북', 'notebook.png');
INSERT INTO categories (name, img) values ('서버', 'server.png');
INSERT INTO categories (name, img) values ('모니터', 'monitor.png');
INSERT INTO categories (name, img) values ('PC본체', 'pc.png');

INSERT INTO departments (name) VALUES ('경영지원부');
INSERT INTO departments (name) VALUES ('영업본부');
INSERT INTO departments (name) VALUES ('제품1팀');
INSERT INTO departments (name) VALUES ('제품1팀(그외)');
INSERT INTO departments (name) VALUES ('제품2팀');
INSERT INTO departments (name) VALUES ('제품2팀(그외)');
INSERT INTO departments (name) VALUES ('제품3팀');
INSERT INTO departments (name) VALUES ('제품4팀');
INSERT INTO departments (name) VALUES ('제품4팀(그외)');
INSERT INTO departments (name) VALUES ('기술영업/컨설팅');
INSERT INTO departments (name) VALUES ('데이터플랫폼사업부');
INSERT INTO departments (name) VALUES ('SI사업부');

-- insert into users (auth,department_id,username) values ('Admin',null,'test');

INSERT INTO users (email, username, auth, department_id)
VALUES ('admin@direa.co.kr', '홍승민', 'Admin', 1);
INSERT INTO users (email, username, auth, department_id)
VALUES ('manager@direa.co.kr', '박지현', 'Manager', 2);
INSERT INTO users (email, username, auth, department_id)
VALUES ('approver@direa.co.kr', '이도현', 'Manager', 3);

INSERT INTO projects (name, code) VALUES ('본사','0001');
INSERT INTO projects (name, code) VALUES ('신한은행 The Next-코어및디지털기반영역_전자금융','0011');
INSERT INTO projects (name, code) VALUES ('신한은행 The Next - 대외대행','0102');
INSERT INTO projects (name, code) VALUES ('올원뱅크 차세대플랫폼 구축','0151');
INSERT INTO projects (name, code) VALUES ('저축은행중앙회 통합채널모니터링시스템 도입', '0152');
INSERT INTO projects (name, code) VALUES ('교원 차세대 영업관리시스템 구축 대외인터페이스(FEP) 개발', '0156');
INSERT INTO projects (name, code) VALUES ('신협중앙회 모바일브랜치 시스템 구축', '0160');
INSERT INTO projects (name, code) VALUES ('삼성카드 마이데이터 구축(에스코어)', '0169');
INSERT INTO projects (name, code) VALUES ('우리카드 인터페이스(EAI) 재구축', '0170');
INSERT INTO projects (name, code) VALUES ('일렉링크(에스트래픽) 업무 개발', '0174');
INSERT INTO projects (name, code) VALUES ('롯데캐피탈 L-ONE 차세대 시스템 구축(MCI,EAI)', '0175');
INSERT INTO projects (name, code) VALUES ('농협은행 MCA웹고도화', '0176');
INSERT INTO projects (name, code) VALUES ('삼성카드 마이데이터 정보제공범위 확대', '0177');
INSERT INTO projects (name, code) VALUES ('신한은행 통합검색포털 구축', '0178');
INSERT INTO projects (name, code) VALUES ('저축은행중앙회 통합채널 솔루션 운영', '0181');
INSERT INTO projects (name, code) VALUES ('신용정보원 마이데이터 중계플랫폼 운영 및 추가기능 개발', '0182');
INSERT INTO projects (name, code) VALUES ('신한은행 콜센터 인프라 재구축', '0186');
INSERT INTO projects (name, code) VALUES ('농협은행 MCI iManager CPM 모니터링 연동', '0190');
INSERT INTO projects (name, code) VALUES ('군인공제회 차세대 회원업무시스템 대외연계 구축', '0191');
INSERT INTO projects (name, code) VALUES ('신한은행 유니버셜 간편앱 대응 API개발', '0194');
INSERT INTO projects (name, code) VALUES ('KAIT eSIM 도입에 따른 동일명의 서비스 추가기능', '0195');
INSERT INTO projects (name, code) VALUES ('롯데카드 인터페이스 고도화 사업', '0196');
INSERT INTO projects (name, code) VALUES ('교직원공제회 ESB 통합시스템 구축', '0198');
INSERT INTO projects (name, code) VALUES ('현대카드 통합 모니터링', '0199');
INSERT INTO projects (name, code) VALUES ('현대캐피탈 Observability 구축', '0200');
INSERT INTO projects (name, code) VALUES ('KAIT 공공 마이데이터 중계시스템 구축', '0202');
INSERT INTO projects (name, code) VALUES ('NH농협/디지털금융 플랫폼 전환(아키텍처/MCI)', '0204');
INSERT INTO projects (name, code) VALUES ('스마트로 OAuth인증시스템 구축', '0205');
INSERT INTO projects (name, code) VALUES ('스카니아 파이낸스 차세대 시스템 구축', '0206');
INSERT INTO projects (name, code) VALUES ('저축은행중앙회 MCI 미통합저축은행 분리 프로젝트', '0207');
INSERT INTO projects (name, code) VALUES ('IBK연금보험 대내외 연계', '0208');
INSERT INTO projects (name, code) VALUES ('한화생명 마이데이터 솔루션 네트워크 방식 변경 커스터마이징', '0209');
INSERT INTO projects (name, code) VALUES ('NH농협카드 통합 디지털 플랫폼 구축_MCI,NMS', '0210');
INSERT INTO projects (name, code) VALUES ('우리 New WON뱅킹 구축', '0211');
INSERT INTO projects (name, code) VALUES ('BNK캐피탈 CruzLink FEP 구축', '0212');
INSERT INTO projects (name, code) VALUES ('신한은행 대직원용 ChatGPT 시스템 연계 개발', '0213');
INSERT INTO projects (name, code) VALUES ('DGB캐피탈 금융결제원 이중화 연계 기술지원', '0214');
INSERT INTO projects (name, code) VALUES ('라이나생명 네이버페이 연동 개발', '0215');
INSERT INTO projects (name, code) VALUES ('신한투자증권 차세대(임시)', '0216');
INSERT INTO projects (name, code) VALUES ('농협손해보험 공공 마이데이터(임시)', '0217');
INSERT INTO projects (name, code) VALUES ('MG손해보험 플랫폼 보험비교 추천서비스 API연동구축', '0218');
INSERT INTO projects (name, code) VALUES ('신한금융그룹 공통 AI컨텍센터 플랫폼 구축', '0219');
INSERT INTO projects (name, code) VALUES ('농협(은행)금융상품 비교 플랫폼 구축', '0220');

INSERT INTO devices (id, user_id, manage_dep, spec, price, model, description, company, sn, status, is_usable, purpose, purchase_date, category_id, project_id)
values ('DIR-N-101', null, 1, 'RAM: 16G CPU: 8core', 1000, 'LSBX2433', '부팅 느림', 'SAMSUNG', 'ND6179NBK', '정상', true, '개발', '2021-09-01', 1, 1);
INSERT INTO devices (id, user_id, manage_dep, spec, price, model, description, company, sn, status, is_usable, purpose, purchase_date, category_id, project_id)
values ('DIR-N-201', 2, 1, 'RAM: 32G CPU: 12core', 1800, 'LG Gram Pro', '반납 예정인 모바일 개발 장비', 'LG', 'SN-2024-201', '정상', false, '모바일 개발', '2024-02-15', 1, 2);
INSERT INTO devices (id, user_id, manage_dep, spec, price, model, description, company, sn, status, is_usable, purpose, purchase_date, category_id, project_id)
values ('DIR-S-301', null, 2, 'RAM: 64G CPU: 16core', 3600, 'Dell PowerEdge', '테스트 환경 서버', 'DELL', 'SN-2023-301', '정상', true, '테스트', '2023-07-10', 2, 3);
-- values ('DIR-N-114', 1, 1, 'RAM: 16G CPU: 8core', 1000, 'LSBX24', '폐기한 기기', 'SAMSUNG', 'ND9NBK', '폐기', false, '개발', '2021-09-02', 1, 1);
--
-- INSERT INTO APPROVALS (APPROVER_ID, USER_ID, APPROVAL_INFO, REASON, CREATED_DATE) values (1, 1, '승인완료', 'test용', '2021-08-01');
-- INSERT INTO APPROVALS (APPROVER_ID, USER_ID, APPROVAL_INFO, REASON) values (null, 1, '승인대기', 'test용');
-- INSERT INTO APPROVALS (APPROVER_ID, USER_ID, APPROVAL_INFO, REASON) values (1, 1, '승인완료', 'test용2');
-- INSERT INTO APPROVALS (APPROVER_ID, USER_ID, APPROVAL_INFO, REASON) values (1, 1, '승인완료', 'test용 폐기');
-- INSERT INTO APPROVALS (APPROVER_ID, USER_ID, APPROVAL_INFO, REASON, CREATED_DATE) values (null, 1, '승인대기', 'test용', '2021-09-01');
--
-- INSERT INTO APPROVAL_DEVICES (id, device_id, img, type) values (1, 'DIR-N-101', 'notebook.png', '구매');
-- INSERT INTO APPROVAL_DEVICES (id, device_id, img, type) values (2, 'DIR-N-102', 'server.png', '폐기');
-- INSERT INTO APPROVAL_DEVICES (id, device_id, img, type) values (3, 'DIR-N-113', 'notebook.png', '대여');
-- INSERT INTO APPROVAL_DEVICES (id, device_id, img, type) values (4, 'DIR-N-114', 'notebook.png', '폐기');
-- INSERT INTO APPROVAL_DEVICES (id, device_id, img, type) values (5, 'DIR-N-101', 'notebook.png', '반납');

INSERT INTO tags (name) values ('OS 설치 필요');
INSERT INTO tags (name) values ('부팅 느림');
INSERT INTO tags (name) values ('정기 점검 필요');
INSERT INTO tags (name) values ('고성능 요구');

INSERT INTO device_tag (device_id, tag_id) values ('DIR-N-101', 1);
INSERT INTO device_tag (device_id, tag_id) values ('DIR-N-101', 2);
INSERT INTO device_tag (device_id, tag_id) values ('DIR-N-201', 3);
INSERT INTO device_tag (device_id, tag_id) values ('DIR-S-301', 4);

INSERT INTO approvals (user_id, approval_info, reason, deadline)
VALUES (2, '승인대기', '노트북 반납 요청', TIMESTAMP '2025-10-31 18:00:00');
INSERT INTO approval_devices (id, device_id, img, type, project_id)
VALUES (1, 'DIR-N-201', 'notebook.png', '반납', 2);

INSERT INTO approvals (user_id, approval_info, reason, deadline)
VALUES (3, '1차승인완료', '테스트 서버 대여 요청', TIMESTAMP '2025-11-05 09:00:00');
INSERT INTO approval_devices (id, device_id, img, type, project_id)
VALUES (2, 'DIR-S-301', 'server.png', '대여', 3);

INSERT INTO approver (user_id, approval_id, is_approved, step)
VALUES (1, 1, false, 1);
INSERT INTO approver (user_id, approval_id, is_approved, step)
VALUES (3, 1, false, 2);
INSERT INTO approver (user_id, approval_id, is_approved, step)
VALUES (1, 2, true, 1);
INSERT INTO approver (user_id, approval_id, is_approved, step)
VALUES (2, 2, false, 2);

/*
INSERT INTO "PUBLIC"."USERS" VALUES
                                 (NULL, NULL, 1, NULL, 'Admin', NULL, 'test'),
                                 (NULL, NULL, 101, NULL, 'Admin', NULL, 'test2'),
                                 (TIMESTAMP '2024-04-09 11:57:40.219031', NULL, 2, TIMESTAMP '2024-04-09 11:57:40.219031', NULL, 'qwer@test.test', 'qwer'),
                                 (TIMESTAMP '2024-04-09 11:57:40.261858', 2, 3, TIMESTAMP '2024-04-09 11:57:40.261858', NULL, 'dhko@direa.co.kr', U&'\ace0\b3c4\d615'),
                                 (TIMESTAMP '2024-04-09 11:57:40.266751', 1, 4, TIMESTAMP '2024-04-09 11:57:40.266751', NULL, 'smgoo@direa.co.kr', U&'\ad6c\c120\bbf8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.272582', NULL, 5, TIMESTAMP '2024-04-09 11:57:40.272582', NULL, 'kyungin_k@direa.co.kr', U&'\ae40\acbd\c778'),
                                 (TIMESTAMP '2024-04-09 11:57:40.278422', 3, 6, TIMESTAMP '2024-04-09 11:57:40.278422', NULL, 'ruddlf2545@direa.co.kr', U&'\ae40\acbd\c77c'),
                                 (TIMESTAMP '2024-04-09 11:57:40.283319', 3, 7, TIMESTAMP '2024-04-09 11:57:40.283319', NULL, 'rhkgkr000@direa.co.kr', U&'\ae40\acbd\c900'),
                                 (TIMESTAMP '2024-04-09 11:57:40.28916', 3, 8, TIMESTAMP '2024-04-09 11:57:40.28916', NULL, 'ksa9382@direa.co.kr', U&'\ae40\ae30\d0dc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.295973', 12, 9, TIMESTAMP '2024-04-09 11:57:40.295973', NULL, 'idrun@direa.co.kr', U&'\ae40\b300\c77c'),
                                 (TIMESTAMP '2024-04-09 11:57:40.301812', NULL, 10, TIMESTAMP '2024-04-09 11:57:40.301812', NULL, 'audwls4545@direa.co.kr', U&'\ae40\ba85\c9c4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.323227', NULL, 11, TIMESTAMP '2024-04-09 11:57:40.323227', NULL, 'miin1635@direa.co.kr', U&'\ae40\bbfc\c218'),
                                 (TIMESTAMP '2024-04-09 11:57:40.329066', NULL, 12, TIMESTAMP '2024-04-09 11:57:40.329066', NULL, 'haksys@direa.co.kr', U&'\ae40\bbfc\c7ac'),
                                 (TIMESTAMP '2024-04-09 11:57:40.334906', 3, 13, TIMESTAMP '2024-04-09 11:57:40.334906', NULL, 'marchis@direa.co.kr', U&'\ae40\bbfc\c7acl'),
                                 (TIMESTAMP '2024-04-09 11:57:40.339773', 5, 14, TIMESTAMP '2024-04-09 11:57:40.339773', NULL, 'kbs@direa.co.kr', U&'\ae40\bc94\c218'),
                                 (TIMESTAMP '2024-04-09 11:57:40.345613', 7, 15, TIMESTAMP '2024-04-09 11:57:40.345613', NULL, 'sjk@direa.co.kr', U&'\ae40\c218\c9c4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.351453', NULL, 16, TIMESTAMP '2024-04-09 11:57:40.351453', NULL, 'rubis99@direa.co.kr', U&'\ae40\c601\d658'),
                                 (TIMESTAMP '2024-04-09 11:57:40.358266', NULL, 17, TIMESTAMP '2024-04-09 11:57:40.358266', NULL, 'forewalk@direa.co.kr', U&'\ae40\c7a5\d6c8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.364106', NULL, 18, TIMESTAMP '2024-04-09 11:57:40.364106', NULL, 'kjm@direa.co.kr', U&'\ae40\c7ac\bbfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.372869', 2, 19, TIMESTAMP '2024-04-09 11:57:40.372869', NULL, 'jskim@direa.co.kr', U&'\ae40\c815\c131'),
                                 (TIMESTAMP '2024-04-09 11:57:40.392394', 5, 20, TIMESTAMP '2024-04-09 11:57:40.392394', NULL, 'jikim@direa.co.kr', U&'\ae40\c9c0\c778'),
                                 (TIMESTAMP '2024-04-09 11:57:40.399206', NULL, 21, TIMESTAMP '2024-04-09 11:57:40.399206', NULL, 'codemasters@direa.co.kr', U&'\ae40\c9c0\d55c'),
                                 (TIMESTAMP '2024-04-09 11:57:40.405046', 12, 22, TIMESTAMP '2024-04-09 11:57:40.405046', NULL, 'jmin@direa.co.kr', U&'\ae40\c9c4\bbfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.411859', 3, 23, TIMESTAMP '2024-04-09 11:57:40.411859', NULL, 'kjy9866@direa.co.kr', U&'\ae40\c9c4\c601'),
                                 (TIMESTAMP '2024-04-09 11:57:40.4177', 5, 24, TIMESTAMP '2024-04-09 11:57:40.4177', NULL, 'kjy4926@direa.co.kr', U&'\ae40\c9c4\c6a9'),
                                 (TIMESTAMP '2024-04-09 11:57:40.424513', 3, 25, TIMESTAMP '2024-04-09 11:57:40.424513', NULL, 'daroguzo@direa.co.kr', U&'\ae40\c9c4\c6b0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.43133', NULL, 26, TIMESTAMP '2024-04-09 11:57:40.43133', NULL, 'taeryongkim@direa.co.kr', U&'\ae40\d0dc\b8e1'),
                                 (TIMESTAMP '2024-04-09 11:57:40.438139', NULL, 27, TIMESTAMP '2024-04-09 11:57:40.438139', NULL, 'haeni0723@direa.co.kr', U&'\ae40\d574\b2c8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.443979', 3, 28, TIMESTAMP '2024-04-09 11:57:40.443979', NULL, 'khg35288@direa.co.kr', U&'\ae40\d604\acb8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.449819', 3, 29, TIMESTAMP '2024-04-09 11:57:40.449819', NULL, 'guswls159357@direa.co.kr', U&'\ae40\d604\c9c4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.454686', 3, 30, TIMESTAMP '2024-04-09 11:57:40.454686', NULL, 'hwanseo3747@direa.co.kr', U&'\ae40\d658\c11c');
INSERT INTO "PUBLIC"."USERS" VALUES
                                 (TIMESTAMP '2024-04-09 11:57:40.461507', 12, 31, TIMESTAMP '2024-04-09 11:57:40.461507', NULL, 'subin@direa.co.kr', U&'\b178\c218\be48'),
                                 (TIMESTAMP '2024-04-09 11:57:40.467353', NULL, 32, TIMESTAMP '2024-04-09 11:57:40.467353', NULL, 'darkknight94@direa.co.kr', U&'\b178\c2b9\d604'),
                                 (TIMESTAMP '2024-04-09 11:57:40.472209', NULL, 33, TIMESTAMP '2024-04-09 11:57:40.472209', NULL, 'direa@direa.co.kr', U&'\b514\b9ac\c544'),
                                 (TIMESTAMP '2024-04-09 11:57:40.478048', NULL, 34, TIMESTAMP '2024-04-09 11:57:40.478048', NULL, 'aws-audit@direa.co.kr', U&'\b514\b9ac\c544(aws-audit)'),
                                 (TIMESTAMP '2024-04-09 11:57:40.482967', NULL, 35, TIMESTAMP '2024-04-09 11:57:40.482967', NULL, 'aws-log-archive@direa.co.kr', U&'\b514\b9ac\c544(aws-log-archive)'),
                                 (TIMESTAMP '2024-04-09 11:57:40.493674', NULL, 36, TIMESTAMP '2024-04-09 11:57:40.493674', NULL, 'noreply@direa.co.kr', U&'\b514\b9ac\c544(\b77c\c774\c120\c2a4)'),
                                 (TIMESTAMP '2024-04-09 11:57:40.497569', NULL, 37, TIMESTAMP '2024-04-09 11:57:40.497569', NULL, 'infra@direa.co.kr', U&'\b514\b9ac\c544(\c778\d504\b77c)'),
                                 (TIMESTAMP '2024-04-09 11:57:40.503414', 3, 38, TIMESTAMP '2024-04-09 11:57:40.503414', NULL, 'mjm0319@direa.co.kr', U&'\bb38\c815\bbfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.509255', 3, 39, TIMESTAMP '2024-04-09 11:57:40.509255', NULL, 'ske0830@direa.co.kr', U&'\bb38\c9c0\c724'),
                                 (TIMESTAMP '2024-04-09 11:57:40.515087', 3, 40, TIMESTAMP '2024-04-09 11:57:40.515087', NULL, 'xonathan.park@direa.co.kr', U&'\bc15\c131\c2dd'),
                                 (TIMESTAMP '2024-04-09 11:57:40.519953', 7, 41, TIMESTAMP '2024-04-09 11:57:40.519953', NULL, 'yesleee@direa.co.kr', U&'\bc15\c608\c2ac'),
                                 (TIMESTAMP '2024-04-09 11:57:40.525793', NULL, 42, TIMESTAMP '2024-04-09 11:57:40.525793', NULL, 'pjw2065@direa.co.kr', U&'\bc15\c815\c6b0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.53066', NULL, 43, TIMESTAMP '2024-04-09 11:57:40.53066', NULL, 'zlkit@direa.co.kr', U&'\bc15\c9c4\d638'),
                                 (TIMESTAMP '2024-04-09 11:57:40.535535', 5, 44, TIMESTAMP '2024-04-09 11:57:40.535535', NULL, 'hkpark@direa.co.kr', U&'\bc15\d604\acbd'),
                                 (TIMESTAMP '2024-04-09 11:57:40.542349', 5, 45, TIMESTAMP '2024-04-09 11:57:40.542349', NULL, 'whee050916@direa.co.kr', U&'\bc15\d718\c751'),
                                 (TIMESTAMP '2024-04-09 11:57:40.54818', 5, 46, TIMESTAMP '2024-04-09 11:57:40.54818', NULL, 'ysbae0928@direa.co.kr', U&'\bc30\c608\c2ac'),
                                 (TIMESTAMP '2024-04-09 11:57:40.552081', NULL, 47, TIMESTAMP '2024-04-09 11:57:40.552081', NULL, 'hkbae@direa.co.kr', U&'\bc30\d604\ae30'),
                                 (TIMESTAMP '2024-04-09 11:57:40.55694', NULL, 48, TIMESTAMP '2024-04-09 11:57:40.55694', NULL, 'jeo100@direa.co.kr', U&'\bc31\c7ac\d638'),
                                 (TIMESTAMP '2024-04-09 11:57:40.561806', 1, 49, TIMESTAMP '2024-04-09 11:57:40.561806', NULL, 'jhbaek@direa.co.kr', U&'\bc31\c9c0\d604'),
                                 (TIMESTAMP '2024-04-09 11:57:40.566699', NULL, 50, TIMESTAMP '2024-04-09 11:57:40.566699', NULL, 'bsw111@direa.co.kr', U&'\bcc0\c131\c6b1'),
                                 (TIMESTAMP '2024-04-09 11:57:40.571556', 7, 51, TIMESTAMP '2024-04-09 11:57:40.571556', NULL, 's0819@direa.co.kr', U&'\c11c\ccad\c6b4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.576423', 1, 52, TIMESTAMP '2024-04-09 11:57:40.576423', NULL, 'ssi1532@direa.co.kr', U&'\c2e0\c218\c778'),
                                 (TIMESTAMP '2024-04-09 11:57:40.585469', 3, 53, TIMESTAMP '2024-04-09 11:57:40.585469', NULL, 'shin7688@direa.co.kr', U&'\c2e0\d615\c8fc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.591309', 3, 54, TIMESTAMP '2024-04-09 11:57:40.591309', NULL, 'wonseokan@direa.co.kr', U&'\c548\c6d0\c11d'),
                                 (TIMESTAMP '2024-04-09 11:57:40.59521', NULL, 55, TIMESTAMP '2024-04-09 11:57:40.59521', NULL, 'dhyang@direa.co.kr', U&'\c591\b3d9\d638'),
                                 (TIMESTAMP '2024-04-09 11:57:40.600069', 3, 56, TIMESTAMP '2024-04-09 11:57:40.600069', NULL, 'rombada@direa.co.kr', U&'\c6a9\c11d\c6d0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.605909', NULL, 57, TIMESTAMP '2024-04-09 11:57:40.605909', NULL, 'sangmoyu@direa.co.kr', U&'\c720\c0c1\baa8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.610775', NULL, 58, TIMESTAMP '2024-04-09 11:57:40.610775', NULL, 'yhs0406@direa.co.kr', U&'\c724\d604\c2dd'),
                                 (TIMESTAMP '2024-04-09 11:57:40.616616', 3, 59, TIMESTAMP '2024-04-09 11:57:40.616616', NULL, 'lkh666666@direa.co.kr', U&'\c774\ac74\d76c');
INSERT INTO "PUBLIC"."USERS" VALUES
                                 (TIMESTAMP '2024-04-09 11:57:40.621482', NULL, 60, TIMESTAMP '2024-04-09 11:57:40.621482', NULL, 'hbdklee@direa.co.kr', U&'\c774\b3d9\ae38'),
                                 (TIMESTAMP '2024-04-09 11:57:40.627322', 5, 61, TIMESTAMP '2024-04-09 11:57:40.627322', NULL, 'imj@direa.co.kr', U&'\c774\bbfc\ad6c'),
                                 (TIMESTAMP '2024-04-09 11:57:40.633162', 7, 62, TIMESTAMP '2024-04-09 11:57:40.633162', NULL, '2sanghoo@direa.co.kr', U&'\c774\c0c1\d6c4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.639002', 3, 63, TIMESTAMP '2024-04-09 11:57:40.639002', NULL, 'mark9352@direa.co.kr', U&'\c774\c2b9\d0dc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.64595', 5, 64, TIMESTAMP '2024-04-09 11:57:40.64595', NULL, 'lsy981004@direa.co.kr', U&'\c774\c2e0\c601'),
                                 (TIMESTAMP '2024-04-09 11:57:40.652378', 3, 65, TIMESTAMP '2024-04-09 11:57:40.652378', NULL, 'zincah@direa.co.kr', U&'\c774\c544\c5f0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.673453', 8, 66, TIMESTAMP '2024-04-09 11:57:40.673453', NULL, 'lyk1209@direa.co.kr', U&'\c774\c601\adfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.679357', 3, 67, TIMESTAMP '2024-04-09 11:57:40.679357', NULL, 'oneqrhee@direa.co.kr', U&'\c774\c6d0\addc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.683946', NULL, 68, TIMESTAMP '2024-04-09 11:57:40.683946', NULL, 'ijooyong@direa.co.kr', U&'\c774\c8fc\c6a9'),
                                 (TIMESTAMP '2024-04-09 11:57:40.689736', 2, 69, TIMESTAMP '2024-04-09 11:57:40.689736', NULL, 'jylee@direa.co.kr', U&'\c774\c9c4\c601'),
                                 (TIMESTAMP '2024-04-09 11:57:40.696417', NULL, 70, TIMESTAMP '2024-04-09 11:57:40.696417', NULL, 'aozamurai@direa.co.kr', U&'\c774\cc2c\c6b0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.700984', 3, 71, TIMESTAMP '2024-04-09 11:57:40.700984', NULL, 'lty@direa.co.kr', U&'\c774\d0dc\c601'),
                                 (TIMESTAMP '2024-04-09 11:57:40.706704', 12, 72, TIMESTAMP '2024-04-09 11:57:40.706704', NULL, 'star9264@direa.co.kr', U&'\c774\d55c\bcc4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.713654', 3, 73, TIMESTAMP '2024-04-09 11:57:40.713654', NULL, 'lhb5098@direa.co.kr', U&'\c774\d55c\bcc4m'),
                                 (TIMESTAMP '2024-04-09 11:57:40.719034', 7, 74, TIMESTAMP '2024-04-09 11:57:40.719034', NULL, 'korez4713@direa.co.kr', U&'\c774\d55c\c0d8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.724602', NULL, 75, TIMESTAMP '2024-04-09 11:57:40.724602', NULL, 'hanheeid@direa.co.kr', U&'\c774\d55c\d76c'),
                                 (TIMESTAMP '2024-04-09 11:57:40.730609', 5, 76, TIMESTAMP '2024-04-09 11:57:40.730609', NULL, 'podoal8281@direa.co.kr', U&'\c774\d604\c11d'),
                                 (TIMESTAMP '2024-04-09 11:57:40.734734', NULL, 77, TIMESTAMP '2024-04-09 11:57:40.734734', NULL, 'khyim@direa.co.kr', U&'\c784\acbd\d638'),
                                 (TIMESTAMP '2024-04-09 11:57:40.739782', 2, 78, TIMESTAMP '2024-04-09 11:57:40.739782', NULL, 'jisol0512@direa.co.kr', U&'\c784\c9c0\c194'),
                                 (TIMESTAMP '2024-04-09 11:57:40.744944', 5, 79, TIMESTAMP '2024-04-09 11:57:40.744944', NULL, 'sjjang@direa.co.kr', U&'\c7a5\c120\c815'),
                                 (TIMESTAMP '2024-04-09 11:57:40.750261', 5, 80, TIMESTAMP '2024-04-09 11:57:40.750261', NULL, 'whilter08@direa.co.kr', U&'\c7a5\c138\d658'),
                                 (TIMESTAMP '2024-04-09 11:57:40.754751', 3, 81, TIMESTAMP '2024-04-09 11:57:40.754751', NULL, 'normalbrand0517@direa.co.kr', U&'\c804\c0c1\d6c8'),
                                 (TIMESTAMP '2024-04-09 11:57:40.75919', 2, 82, TIMESTAMP '2024-04-09 11:57:40.75919', NULL, 'bdchung@direa.co.kr', U&'\c815\bcd1\b3c4'),
                                 (TIMESTAMP '2024-04-09 11:57:40.764859', NULL, 83, TIMESTAMP '2024-04-09 11:57:40.764859', NULL, 'shjeong@direa.co.kr', U&'\c815\c2b9\d658'),
                                 (TIMESTAMP '2024-04-09 11:57:40.770316', 1, 84, TIMESTAMP '2024-04-09 11:57:40.770316', NULL, 'jke@direa.co.kr', U&'\c870\ace0\c740'),
                                 (TIMESTAMP '2024-04-09 11:57:40.775444', 5, 85, TIMESTAMP '2024-04-09 11:57:40.775444', NULL, 'sungdae@direa.co.kr', U&'\c870\c131\b300'),
                                 (TIMESTAMP '2024-04-09 11:57:40.783675', 3, 86, TIMESTAMP '2024-04-09 11:57:40.783675', NULL, 'dory0107@direa.co.kr', U&'\c870\c218\acbd'),
                                 (TIMESTAMP '2024-04-09 11:57:40.790003', 7, 87, TIMESTAMP '2024-04-09 11:57:40.790003', NULL, 'kite0728@direa.co.kr', U&'\c870\c5f0\c8fc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.795535', 3, 88, TIMESTAMP '2024-04-09 11:57:40.795535', NULL, 'whwo331@direa.co.kr', U&'\c870\c7ac\c5f0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.802984', 7, 89, TIMESTAMP '2024-04-09 11:57:40.802984', NULL, 'johyojun@direa.co.kr', U&'\c870\d6a8\c900');
INSERT INTO "PUBLIC"."USERS" VALUES
                                 (TIMESTAMP '2024-04-09 11:57:40.808175', NULL, 90, TIMESTAMP '2024-04-09 11:57:40.808175', NULL, 'eunyoung.ju@direa.co.kr', U&'\c8fc\c740\c601'),
                                 (TIMESTAMP '2024-04-09 11:57:40.814388', NULL, 91, TIMESTAMP '2024-04-09 11:57:40.814388', NULL, 'csh@direa.co.kr', U&'\cc28\c21c\d638'),
                                 (TIMESTAMP '2024-04-09 11:57:40.819873', 3, 92, TIMESTAMP '2024-04-09 11:57:40.819873', NULL, 'jkoppyu@direa.co.kr', U&'\cc44\c1a1\bbfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.826389', 7, 93, TIMESTAMP '2024-04-09 11:57:40.826389', NULL, 'sjoon97@direa.co.kr', U&'\cd5c\c2b9\c900'),
                                 (TIMESTAMP '2024-04-09 11:57:40.83141', 7, 94, TIMESTAMP '2024-04-09 11:57:40.83141', NULL, 'chl0316@direa.co.kr', U&'\cd5c\c601\bbfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.839175', 3, 95, TIMESTAMP '2024-04-09 11:57:40.839175', NULL, 'cjy051702@direa.co.kr', U&'\cd5c\c815\c724'),
                                 (TIMESTAMP '2024-04-09 11:57:40.844881', NULL, 96, TIMESTAMP '2024-04-09 11:57:40.844881', NULL, 'cjh947@direa.co.kr', U&'\cd5c\c9c0\d638'),
                                 (TIMESTAMP '2024-04-09 11:57:40.8504', NULL, 97, TIMESTAMP '2024-04-09 11:57:40.8504', NULL, 'jinweon.choi@direa.co.kr', U&'\cd5c\c9c4\c6d0'),
                                 (TIMESTAMP '2024-04-09 11:57:40.856186', NULL, 98, TIMESTAMP '2024-04-09 11:57:40.856186', NULL, 'fabel@direa.co.kr', U&'\cd5c\d615\c218'),
                                 (TIMESTAMP '2024-04-09 11:57:40.862667', 3, 99, TIMESTAMP '2024-04-09 11:57:40.862667', NULL, 'chkchk610@direa.co.kr', U&'\cd5c\d61c\adfc'),
                                 (TIMESTAMP '2024-04-09 11:57:40.867871', NULL, 100, TIMESTAMP '2024-04-09 11:57:40.867871', NULL, 'syhann@direa.co.kr', U&'\d55c\c18c\c601');
*/
