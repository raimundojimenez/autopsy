"""
Autopsy Forensic Browser

Copyright 2019 Basis Technology Corp.
Contact: carrier <at> sleuthkit <dot> org

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
from java.io import File
from java.lang import Class
from java.lang import ClassNotFoundException
from java.lang import Long
from java.lang import String
from java.sql import ResultSet
from java.sql import SQLException
from java.sql import Statement
from java.util.logging import Level
from java.util import ArrayList
from org.apache.commons.codec.binary import Base64
from org.sleuthkit.autopsy.casemodule import Case
from org.sleuthkit.autopsy.coreutils import Logger
from org.sleuthkit.autopsy.coreutils import MessageNotifyUtil
from org.sleuthkit.autopsy.coreutils import AppSQLiteDB

from org.sleuthkit.autopsy.datamodel import ContentUtils
from org.sleuthkit.autopsy.ingest import IngestJobContext
from org.sleuthkit.datamodel import AbstractFile
from org.sleuthkit.datamodel import BlackboardArtifact
from org.sleuthkit.datamodel import BlackboardAttribute
from org.sleuthkit.datamodel import Content
from org.sleuthkit.datamodel import TskCoreException
from org.sleuthkit.datamodel.Blackboard import BlackboardException
from org.sleuthkit.autopsy.casemodule import NoCurrentCaseException
from org.sleuthkit.datamodel import Account
from org.sleuthkit.datamodel.blackboardutils import CommunicationArtifactsHelper
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import MessageReadStatus
from org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper import CommunicationDirection
from TskContactsParser import TskContactsParser
from TskMessagesParser import TskMessagesParser
from TskCallLogsParser import TskCallLogsParser

import traceback
import general

class LineAnalyzer(general.AndroidComponentAnalyzer):
    """
        Parses the Line App databases for TSK contacts & message artifacts.
    """

    def __init__(self):
        self._logger = Logger.getLogger(self.__class__.__name__)
        self._LINE_PACKAGE_NAME = "jp.naver.line.android"
        self._PARSER_NAME = "Line Parser"
        self._VERSION = "9.15.1"

    def analyze(self, dataSource, fileManager, context):
        try:
            contact_and_message_dbs = AppSQLiteDB.findAppDatabases(dataSource, 
                     "naver_line", True, self._LINE_PACKAGE_NAME)
            calllog_dbs = AppSQLiteDB.findAppDatabases(dataSource,
                     "call_history", True, self._LINE_PACKAGE_NAME)

            for contact_and_message_db in contact_and_message_dbs:
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                            current_case.getSleuthkitCase(), self._PARSER_NAME, 
                            contact_and_message_db.getDBFile(), Account.Type.LINE) 
                self.parse_contacts(contact_and_message_db, helper)
                self.parse_messages(contact_and_message_db, helper)

            for calllog_db in calllog_dbs:
                current_case = Case.getCurrentCaseThrows()
                helper = CommunicationArtifactsHelper(
                            current_case.getSleuthkitCase(), self._PARSER_NAME,
                            calllog_db.getDBFile(), Account.Type.LINE)
                self.parse_calllogs(dataSource, calllog_db, helper)

        except NoCurrentCaseException as ex:
            # Error parsing Line databases.
            self._logger.log(Level.WARNING, "Error parsing the Line App Databases", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   
        
        for contact_and_message_db in contact_and_message_dbs:
            contact_and_message_db.close()

        for calllog_db in calllog_dbs:
            calllog_db.close()

    def parse_contacts(self, contacts_db, helper):
        try:
            contacts_parser = LineContactsParser(contacts_db)
            while contacts_parser.next():
                helper.addContact( 
                    contacts_parser.get_account_name(), 
                    contacts_parser.get_contact_name(), 
                    contacts_parser.get_phone(),
                    contacts_parser.get_home_phone(),
                    contacts_parser.get_mobile_phone(),
                    contacts_parser.get_email()
                )
            contacts_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error parsing the Line App Database for contacts", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   
        except TskCoreException as ex:
            #Error adding artifact to case database... case is not complete.
            self._logger.log(Level.SEVERE, 
                    "Error adding Line contact artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())   
        except BlackboardException as ex:
            #Error posting notification to blackboard
            self._logger.log(Level.WARNING, 
                    "Error posting Line contact artifacts to blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   

    def parse_calllogs(self, dataSource, calllogs_db, helper):
        try:
            calllogs_db.attachDatabase(
                        dataSource, "naver_line", 
                        calllogs_db.getDBFile().getParentPath(), "naver")

            calllog_parser = LineCallLogsParser(calllogs_db)
            while calllog_parser.next():
                helper.addCalllog(
                    calllog_parser.get_call_direction(),
                    calllog_parser.get_phone_number_from(),
                    calllog_parser.get_phone_number_to(),
                    calllog_parser.get_call_start_date_time(),
                    calllog_parser.get_call_end_date_time(),
                    calllog_parser.get_call_type()
                )
            calllog_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error parsing the Line App Database for calllogs", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   
        except TskCoreException as ex:
            #Error adding artifact to case database... case is not complete.
            self._logger.log(Level.SEVERE, 
                    "Error adding Line calllog artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())   
        except BlackboardException as ex:
            #Error posting notification to blackboard
            self._logger.log(Level.WARNING, 
                    "Error posting Line calllog artifacts to blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   
    
    def parse_messages(self, messages_db, helper):
        try:
           
            messages_parser = LineMessagesParser(messages_db)
            while messages_parser.next():
                helper.addMessage(
                    messages_parser.get_message_type(),
                    messages_parser.get_message_direction(),
                    messages_parser.get_phone_number_from(),
                    messages_parser.get_phone_number_to(),
                    messages_parser.get_message_date_time(),
                    messages_parser.get_message_read_status(),
                    messages_parser.get_message_subject(),
                    messages_parser.get_message_text(),
                    messages_parser.get_thread_id() 
                )
            messages_parser.close()
        except SQLException as ex:
            self._logger.log(Level.WARNING, "Error parsing the Line App Database for messages.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   
        except TskCoreException as ex:
            #Error adding artifact to case database... case is not complete.
            self._logger.log(Level.SEVERE, 
                    "Error adding Line message artifacts to the case database.", ex)
            self._logger.log(Level.SEVERE, traceback.format_exc())   
        except BlackboardException as ex:
            #Error posting notification to blackboard
            self._logger.log(Level.WARNING, 
                    "Error posting Line message artifacts to blackboard.", ex)
            self._logger.log(Level.WARNING, traceback.format_exc())   

class LineCallLogsParser(TskCallLogsParser):
    """
        Parses out TSK_CALLLOG information from the Line database.
        TSK_CALLLOG fields that are not in the line database are given
        a default value inherited from the super class.
    """

    def __init__(self, calllog_db):
        super(LineCallLogsParser, self).__init__(calllog_db.runQuery(
                 """
                     SELECT substr(CallH.call_type, -1) AS direction, 
                            CallH.start_time            AS start_time, 
                            CallH.end_time              AS end_time, 
                            ConT.server_name            AS name, 
                            CallH.voip_type             AS call_type, 
                            ConT.m_id 
                            FROM   call_history AS CallH 
                                   JOIN naver.contacts AS ConT 
                                     ON CallH.caller_mid = ConT.m_id
                 """
             )
        )
        self._OUTGOING_CALL_TYPE = "O"
        self._INCOMING_CALL_TYPE = "I"
        self._VIDEO_CALL_TYPE = "V"
        self._AUDIO_CALL_TYPE = "A"

    def get_call_direction(self):
        direction = self.result_set.getString("direction")
        if direction == self._OUTGOING_CALL_TYPE:
            return self.OUTGOING_CALL
        return self.INCOMING_CALL

    def get_call_start_date_time(self):
        try:
            return long(self.result_set.getString("start_time")) / 1000
        except ValueError as ve:
            return super(LineCallLogsParser, self).get_call_start_date_time()

    def get_call_end_date_time(self):
        try:
            return long(self.result_set.getString("end_time")) / 1000
        except ValueError as ve:
            return super(LineCallLogsParser, self).get_call_end_date_time()
    
    def get_phone_number_to(self):
        if self.get_call_direction() == self.OUTGOING_CALL:
            return Account.Address(self.result_set.getString("m_id"), 
                        self.result_set.getString("name"))
        return super(LineCallLogsParser, self).get_phone_number_to()

    def get_phone_number_from(self):
        if self.get_call_direction() == self.INCOMING_CALL:
            return Account.Address(self.result_set.getString("m_id"),
                        self.result_set.getString("name"))
        return super(LineCallLogsParser, self).get_phone_number_from()

    def get_call_type(self):
        if self.result_set.getString("call_type") == self._VIDEO_CALL_TYPE:
            return self.VIDEO_CALL
        if self.result_set.getString("call_type") == self._AUDIO_CALL_TYPE:
            return self.AUDIO_CALL
        return super(LineCallLogsParser, self).get_call_type()

class LineContactsParser(TskContactsParser):
    """
        Parses out TSK_CONTACT information from the Line database.
        TSK_CONTACT fields that are not in the line database are given
        a default value inherited from the super class. 
    """

    def __init__(self, contact_db):
        super(LineContactsParser, self).__init__(contact_db.runQuery(
                 """
                     SELECT m_id,
                            server_name
                     FROM   contacts
                 """
              )
        )
    def get_account_name(self):
        return self.result_set.getString("m_id")

    def get_contact_name(self):
        return self.result_set.getString("server_name")

class LineMessagesParser(TskMessagesParser):
    """
        Parse out TSK_MESSAGE information from the Line database.
        TSK_MESSAGE fields that are not in the line database are given
        a default value inherited from the super class.
    """

    def __init__(self, message_db):
        super(LineMessagesParser, self).__init__(message_db.runQuery(
                """
                    SELECT contact_list_with_groups.name,
                           contact_list_with_groups.id,
                           contact_list_with_groups.members,
                           contact_list_with_groups.member_names,
                           CH.from_mid,
                           C.server_name AS from_name,
                           CH.content,
                           CH.created_time,
                           CH.attachement_type,
                           CH.attachement_local_uri,
                           CH.status
                    FROM   (SELECT G.name,
                                   group_members.id,
                                   group_members.members,
                                   group_members.member_names
                            FROM   (SELECT id,
                                           group_concat(M.m_id) AS members,
                                           group_concat(replace(C.server_name, 
                                                                ",", 
                                                                "")) as member_names
                                    FROM   membership AS M
                                           JOIN contacts as C
                                             ON M.m_id = C.m_id
                                    GROUP  BY id) AS group_members
                                   JOIN groups AS G
                                     ON G.id = group_members.id
                            UNION
                            SELECT server_name,
                                   m_id,
                                   NULL,
                                   NULL
                            FROM   contacts) AS contact_list_with_groups
                           JOIN chat_history AS CH
                             ON CH.chat_id = contact_list_with_groups.id
                           LEFT JOIN contacts as C
                             ON C.m_id = CH.from_mid
                    WHERE attachement_type != 6
                """
             )
        )
        self._LINE_MESSAGE_TYPE = "Line Message"
        #From the limited test data, it appeared that incoming
        #was only associated with a 1 status. Status # 3 and 7
        #was only associated with outgoing.
        self._INCOMING_MESSAGE_TYPE = 1

    def get_message_type(self):
        return self._LINE_MESSAGE_TYPE

    def get_message_date_time(self):
        created_time = self.result_set.getString("created_time")
        try:
            #Get time in seconds (created_time is stored in ms from epoch)
            return long(created_time) / 1000
        except ValueError as ve:
            return super(LineMessagesParser, self).get_message_date_time()

    def get_message_text(self):
        content = self.result_set.getString("content") 
        attachment_uri = self.result_set.getString("attachement_local_uri")
        if attachment_uri is not None and content is not None:
            return general.appendAttachmentList(content, [attachment_uri])
        elif attachment_uri is not None and content is None:
            return general.appendAttachmentList("", [attachment_uri])
        return content

    def get_message_direction(self):  
        if self.result_set.getInt("status") == self._INCOMING_MESSAGE_TYPE:
            return self.INCOMING
        return self.OUTGOING

    def get_phone_number_from(self):
        if self.get_message_direction() == self.INCOMING:
            from_mid = self.result_set.getString("from_mid")
            if from_mid is not None:
                return Account.Address(from_mid,
                         self.result_set.getString("from_name"))
        return super(LineMessagesParser, self).get_phone_number_from()

    def get_phone_number_to(self):
        if self.get_message_direction() == self.OUTGOING:
            group = self.result_set.getString("members")
            if group is not None:
                group = group.split(",")
                names = self.result_set.getString("member_names").split(",")
                
                recipients = []

                for recipient_id, recipient_name in zip(group, names):
                    recipients.append(Account.Address(recipient_id, recipient_name))

                return recipients

            return Account.Address(self.result_set.getString("id"), 
                    self.result_set.getString("name"))

        return super(LineMessagesParser, self).get_phone_number_to()

    def get_thread_id(self):
        members = self.result_set.getString("members")
        if members is not None:
            return self.result_set.getString("id")
        return super(LineMessagesParser, self).get_thread_id()
