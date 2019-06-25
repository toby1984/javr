; ENC28J60 Bank 0 registers
.equ ETH_BANK0_ERDPTL = 0x00
.equ ETH_BANK0_ERDPTH = 0x01
.equ ETH_BANK0_EWRPTL = 0x02
.equ ETH_BANK0_EWRPTH = 0x03
.equ ETH_BANK0_ETXSTL = 0x04
.equ ETH_BANK0_ETXSTH = 0x05
.equ ETH_BANK0_ETXNDL = 0x06
.equ ETH_BANK0_ETXNDH = 0x07
.equ ETH_BANK0_ERXSTL = 0x08
.equ ETH_BANK0_ERXSTH = 0x09
.equ ETH_BANK0_ERXNDL = 0x0A
.equ ETH_BANK0_ERXNDH = 0x0B
.equ ETH_BANK0_ERXRDPTL = 0x0C
.equ ETH_BANK0_ERXRDPTH = 0x0D
.equ ETH_BANK0_ERXWRPTL = 0x0E
.equ ETH_BANK0_ERXWRPTH = 0x0F
.equ ETH_BANK0_EDMASTL = 0x10
.equ ETH_BANK0_EDMASTH = 0x11
.equ ETH_BANK0_EDMANDL = 0x12
.equ ETH_BANK0_EDMANDH = 0x13
.equ ETH_BANK0_EDMADSTL = 0x14
.equ ETH_BANK0_EDMADSTH = 0x15
.equ ETH_BANK0_EDMACSL = 0x16 
.equ ETH_BANK0_EDMACSH = 0x17 

; ENC28J60 common registers (available in every bank)
.equ ETH_EIE =  0x1B
.equ ETH_EIR =  0x1C
.equ ETH_ESTAT =  0x1D
.equ ETH_ECON2 = 0x1E
.equ ETH_ECON1 = 0x1F
 
; ENC28J60 Bank 1 registers
.equ ETH_BANK1_EHT0  = 0x00
.equ ETH_BANK1_EHT1  = 0x01
.equ ETH_BANK1_EHT2  = 0x02
.equ ETH_BANK1_EHT3  = 0x03
.equ ETH_BANK1_EHT4  = 0x04
.equ ETH_BANK1_EHT5  = 0x05
.equ ETH_BANK1_EHT6  = 0x06
.equ ETH_BANK1_EHT7  = 0x07
.equ ETH_BANK1_EPMM0  = 0x08
.equ ETH_BANK1_EPMM1  = 0x09
.equ ETH_BANK1_EPMM2  = 0x0A
.equ ETH_BANK1_EPMM3  = 0x0B
.equ ETH_BANK1_EPMM4  = 0x0C
.equ ETH_BANK1_EPMM5  = 0x0D
.equ ETH_BANK1_EPMM6  = 0x0E
.equ ETH_BANK1_EPMM7  = 0x0F
.equ ETH_BANK1_EPMCSL  = 0x10
.equ ETH_BANK1_EPMCSH  = 0x11
.equ ETH_BANK1_EPMOL  = 0x14
.equ ETH_BANK1_EPMOH  = 0x15
.equ ETH_BANK1_ERXFCON  = 0x18
.equ ETH_BANK1_EPKTCNT  = 0x19

; ENC28J60 Bank 2 registers
.equ MACON1  = 0x00
.equ Reserved  = 0x01
.equ MACON3  = 0x02
.equ MACON4  = 0x03
.equ MABBIPG  = 0x04
.equ MAIPGL  = 0x06
.equ MAIPGH  = 0x07
.equ MACLCON1  = 0x08
.equ MACLCON2  = 0x09
.equ MAMXFLL  = 0x0A
.equ MAMXFLH  = 0x0B
.equ MICMD  = 0x12
.equ MIREGADR  = 0x14
.equ MIWRL  = 0x16
.equ MIWRH  = 0x17
.equ MIRDL  = 0x18
.equ MIRDH  = 0x19
 
; ENC28J60 Bank 3 registers
.equ  MAADR5 = 0x00
.equ  MAADR6 = 0x01
.equ  MAADR3 = 0x02
.equ  MAADR4 = 0x03
.equ  MAADR1 = 0x04
.equ  MAADR2 = 0x05
.equ  EBSTSD = 0x06
.equ  EBSTCON = 0x07
.equ  EBSTCSL = 0x08
.equ  EBSTCSH = 0x09
.equ  MISTAT = 0x0A
.equ  EREVID = 0x12
.equ  ECOCON = 0x15
.equ  EFLOCON = 0x17
.equ  EPAUSL = 0x18
.equ  EPAUSH = 0x19