import scrapy
from bs4 import BeautifulSoup

class OgorodSpider(scrapy.Spider):
    name = "ogorod"
    cnt = 0
    limit = 5000

    def start_requests(self):
        urls = [
            'https://www.ogorod.ru/forum/',

        ]
        for url in urls:
            yield scrapy.Request(url=url, callback=self.parse_forum)
    def parse_forum(self, response):
        for topic in response.css('h4.ipsDataItem_title a::attr(href)'):
          yield scrapy.Request(topic.get(), callback=self.parse_topic)
    def parse_topic(self, response):
        for theme in response.css('h4.ipsDataItem_title a::attr(href)'):
          yield scrapy.Request(theme.get(), callback=self.parse_theme)
    def parse_theme(self, response):
        divs =  response.css('div.ipsType_normal').getall()
        with open('log.txt', 'a') as f:
          for div in divs:
            soup = BeautifulSoup(div) 
            for a in soup.find_all('p'):
              if a.string != '\xa0' and a.string != None and self.cnt < self.limit:
                self.cnt += 1
                yield{'text' : a.string}
            
        
        
