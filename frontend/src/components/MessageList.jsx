import { format } from "date-fns"
import { ko } from 'date-fns/locale';
import { Link, RotateCw, Phone, Bold } from "lucide-react"
import ReactMarkdown from 'react-markdown'
import bot from '../assets/Bot_icon.svg'

function MessageList({ messageList, botClass, handleSubmit, handleRetry, isLoading, handleDownload, lastUserMessageRef }) {
  
  const lastUserIndex = [...messageList].map(m => m.senderType).lastIndexOf('USER')

  // User Class 정리
  const userClass = 
  `rounded-tl-[24px]
  rounded-tr-[4px]
  lg:rounded-tr-[8px]
  rounded-bl-[24px]
  rounded-br-[24px]
  text-[#FFFFFF]
  text-[12px]
  md:text-[16px]
  text-left
  max-w-[310px]
  md:max-w-[550px]
  lg:max-w-[800px]
  p-[16px]
  md:px-[24px]
  whitespace-pre-wrap
  mt-[20px]
  md:mr-[42px]
  drop-shadow
  `

  return(
    <div>
      {messageList.map((message, index) => {
        const currentDate = message.createdAt?.slice(0, 10)
        const prevDate = index > 0 ? messageList[index - 1].createdAt?.slice(0, 10) : null
        const isNewDate = currentDate !== prevDate

          return(
          <div key={message.id} ref={index === lastUserIndex ? lastUserMessageRef : null}>
            {/* 날짜 구분선 */}
            {isNewDate && (
              <div className="flex items-center justify-center">
                <p className="border-[1px] border-[#DEE2E6] bg-[#FFFFFF] w-[130px] md:w-[150px] py-[6px] px-[16px] rounded-[9999px] drop-shadow-sm text-[#495057] text-[12px] md:text-[14px] text-center mt-[16px]">
                  {currentDate && format(new Date(currentDate), 'yyyy년 M월 d일', {locale: ko})}</p>
              </div>)}
            
            {/* 말풍선 영역 */}
            <div className={
              message.senderType === 'USER' ? 'flex justify-end' : 'flex justify-start items-start mt-[32px]'}>
              {message.senderType === 'BOT' && <img src={bot} alt="WithBuddy 채팅봇 이미지"/>}
              <div className="flex flex-col">
                <div className={
                  message.senderType === 'USER' ? `${userClass}` : `${botClass}`}
                  style={message.senderType === 'USER' ? {background: 'linear-gradient(to right, #7DC1FF, #6BB5F2, #57A7E4, #4F9CD7, #4791CA)'} : {}}>

                  {/* 에러 메시지/재시도 버튼 */}
                  {message.messageType === 'ai_timeout' || message.messageType === 'send_error' ? (
                    <div className="flex flex-col md:flex-row items-start md:items-center gap-[10px]">
                      <p className="text-[#495057] text-[14px] lg:text-[16px]">{message.content}</p>
                      <button 
                      onClick={handleRetry} 
                      disabled={isLoading}
                      className={`flex items-center justify-cnter gap-[5px] text-[12px] rounded-[9999px] py-[6px] px-[12px]  ${message.messageType === 'ai_timeout' ? 'bg-[#EAF6FF] text-[#204867] hover:bg-[#D2E2F6]' : 'bg-[#336B974D] text-[#FFFFFF] hover:bg-[#336B97B2]'}`}>
                        <RotateCw size={14} />다시 물어보기
                      </button>
                    </div>)
                    :
                    (<>
                    {/* 메시지 내용 */}
                    <ReactMarkdown>{message.content}</ReactMarkdown>

                    {/* 문서 출처 */}
                    {message.documents && message.documents.length > 0 && message.messageType !== 'no_result' && message.messageType !== 'out_of_scope' && (
                      <div>
                        <br/>
                        <p className="font-bold">문서 출처</p>
                        <div  className="inline-flex flex-col">
                        {message.documents.map((doc) => (
                          <div key={doc.documentId} className="items-start py-[10px] px-[16px] rounded-[8px] border-[1px] border-[#336B971A] bg-[#F7FBFF5A] h-auto mt-[8px]">
                            <div className="flex flex-col gap-[6px]">
                              <p className='text-[12px]'>{doc.title}</p>
                              {doc.file && (
                                <div className="flex flex-wrap gap-[8px]">
                                  <div className="flex items-start gap-[2px] text-[#495057] text-[12px]">
                                    <Link size={12} className="mt-[3px]"/>
                                    <p className="break-all">{doc.file.fileName}</p>
                                  </div>
                                    <button onClick={() => handleDownload(doc.file.downloadUrl, doc.file.fileName)} className="text-[11px] text-[#336B97] underline">다운로드</button>
                                </div>
                              )}
                            </div>
                          </div>
                        ))}
                        </div>
                      </div>
                    )}

                    {/* 담당자 카드 */}
                    {message.recommendedContacts?.length > 0 && (
                      <div className="inline-flex flex-col mt-[8px]">
                        {message.recommendedContacts.map((contact) => (
                          <div key={`${contact.department}-${contact.name}`} className="flex items-center gap-[12px] py-[10px] px-[16px] rounded-[8px] border-[1px] border-[#336B9733] bg-[#F7FBFF] mt-[8px]">
                            {/* 전화기 아이콘 */}
                            <div className="flex items-center justify-center w-[26px] h-[26px] rounded-[4px] bg-[#FFFFFF] flex-shrink-0">
                              <Phone size={14} className="text-[#336B97]"/>
                            </div>
                            {/* 담당자 정보 */}
                            <div className="flex flex-col gap-[2px]">
                              <p className="text-[12px] text-[#336B97] font-medium">{contact.department} {contact.name} {contact.position}</p>
                              {contact.connects.map((c) => (
                                <div key={c.type}>
                                  <p className="text-[11px] text-[#868E96] font-medium">{c.type} | <span>{c.value}</span></p>
                                </div>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                    </>)
                  }
                </div>

                {/* 버디 넛지 빠른 질문 */}
                {message.messageType === 'suggestion' && message.quickTaps?.length > 0 && (
                  <div className="flex flex-wrap gap-[8px] mt-[8px] ml-[16px]">
                    {message.quickTaps.map((tap) => (
                      <button
                        key={tap.eventTarget}
                        onClick={() => handleSubmit(null, tap.content)}
                        className="border-[1px] border-[#DEE2E6] py-[8px] px-[16px] rounded-[9999px] text-[#868E96] text-[11px] md:text-[12px] shadow-md">
                        {tap.buttonText}
                      </button>
                    ))}
                  </div>
                )}

                {/* 시간 */}
                <div className={
                  `${message.senderType === 'USER' ? 'text-right md:mr-[48px]' : 'text-left ml-[16px]'}`}>
                  <p className="text-[#868E96] text-[10px] md:text-[16px] mt-[8px]">{message.createdAt && format(new Date(message.createdAt), 'a h:mm', {locale: ko})}</p>
                </div>
              </div>
            </div>
          </div>
          )
        })
      }
    </div>
  )
}

export default MessageList