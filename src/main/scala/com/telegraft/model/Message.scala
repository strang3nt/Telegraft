package com.telegraft.model

final case class Message(
                          id: Long,
                          userId: Long,
                          chatId: Long,
                          content: String
                        )
